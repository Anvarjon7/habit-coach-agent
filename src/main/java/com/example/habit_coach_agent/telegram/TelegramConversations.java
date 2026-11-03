package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.service.HabitService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TelegramConversations {
    private final JdbcTemplate jdbc;
    private final TelegramStore store;
    private final HabitService habits;
    private final Clock clock;
    private final int dailyText;
    private final int dailyVoice;
    private final int dailyGlobal;
    public TelegramConversations(JdbcTemplate jdbc,TelegramStore store,HabitService habits,Clock clock,
            @Value("${telegram.daily-text-limit:20}") int dailyText,
            @Value("${telegram.daily-voice-limit:5}") int dailyVoice,
            @Value("${telegram.daily-global-limit:200}") int dailyGlobal) {
        this.jdbc=jdbc; this.store=store; this.habits=habits; this.clock=clock;
        if(dailyText<1 || dailyText>1000 || dailyVoice<1 || dailyVoice>100 || dailyGlobal<1 || dailyGlobal>10000)
            throw new IllegalStateException("Invalid Telegram AI usage limits");
        this.dailyText=dailyText; this.dailyVoice=dailyVoice; this.dailyGlobal=dailyGlobal;
    }
    // Called inside the ingress transaction after addressing checks.
    void enqueue(TelegramUpdate update) {
        Optional<TelegramMember> found=store.member(update.chatId(),update.userId());
        if(found.isEmpty() || !found.get().active()) {
            store.reply(update,"Join voluntarily with /join before coaching. /help explains the controls.","text"); return;
        }
        TelegramMember member=found.get();
        if(update.messageId()<=member.lastMessageId()) return;
        store.advance(member,update.messageId());
        if(!member.aiConsent()) {
            store.reply(update,"To use AI coaching or voice understanding, first choose /ai on. Addressed messages, voice, your own goals and interests may be sent to the configured AI provider. /ai off stops this. Habit commands work without AI.","text"); return;
        }
        if(!update.voiceFileId().isBlank() && (update.voiceFileId().length()>512 || update.voiceDuration()<1 || update.voiceDuration()>60 || update.voiceSize()>5*1024*1024)) {
            store.reply(update,"Use a Telegram voice note up to 60 seconds and 5 MiB, or send text.","text"); return;
        }
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE membership_id=? AND status IN ('PENDING','PROCESSING')",Integer.class,member.id());
        Integer recent=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE membership_id=? AND created_at>?",Integer.class,member.id(),Timestamp.from(clock.instant().minusSeconds(10)));
        Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE status IN ('PENDING','PROCESSING')",Integer.class);
        if(pending>=3 || recent>=1 || total>=1000) { store.reply(update,"I'm still working on recent requests. Please wait a moment before asking again.","text"); return; }
        String kind=update.voiceFileId().isBlank()?"TEXT":"VOICE";
        Timestamp localDay=Timestamp.from(java.time.LocalDate.now(clock.withZone(ZoneId.of(member.timezone()))).atStartOfDay(ZoneId.of(member.timezone())).toInstant());
        java.time.LocalDate utcDay=java.time.LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC));
        Integer memberUsed=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE membership_id=? AND input_kind=? AND created_at>=?",Integer.class,member.id(),kind,localDay);
        List<Integer> globalCounts=jdbc.queryForList("SELECT accepted_requests FROM telegram_ai_daily_totals WHERE day_utc=?",Integer.class,utcDay);
        int globalUsed=globalCounts.isEmpty()?0:globalCounts.getFirst();
        int operations=kind.equals("VOICE")?2:1;
        if(memberUsed>=(kind.equals("VOICE")?dailyVoice:dailyText) || globalUsed+operations>dailyGlobal) {
            store.reply(update,"Today's AI coaching allowance has been reached. Habit commands still work; coaching resets on the next day.","text"); return;
        }
        jdbc.update("""
                INSERT INTO telegram_conversations(membership_id,message_id,message_thread_id,message_text,voice_file_id,input_kind,created_at)
                VALUES (?,?,?,?,?,?,?)
                """,member.id(),update.messageId(),update.threadId(),update.text(),update.voiceFileId(),kind,Timestamp.from(clock.instant()));
        if(globalCounts.isEmpty()) jdbc.update("INSERT INTO telegram_ai_daily_totals(day_utc,accepted_requests) VALUES (?,?)",utcDay,operations);
        else jdbc.update("UPDATE telegram_ai_daily_totals SET accepted_requests=accepted_requests+? WHERE day_utc=?",operations,utcDay);
    }

    @Transactional
    public Optional<TelegramConversationJob> claim() {
        store.lockIngress();
        // A crashed external AI request may already have been billed. Do not automatically repeat it.
        jdbc.update("UPDATE telegram_conversations SET status='FAILED',message_text=NULL,voice_file_id=NULL,lease_token=NULL WHERE status='PROCESSING' AND lease_until<=?",Timestamp.from(clock.instant()));
        List<Long> ids=jdbc.queryForList("""
                SELECT id FROM telegram_conversations WHERE status='PENDING'
                ORDER BY id LIMIT 1 FOR UPDATE
                """,Long.class);
        if(ids.isEmpty()) return Optional.empty();
        long id=ids.getFirst();
        long membership=jdbc.queryForObject("SELECT membership_id FROM telegram_conversations WHERE id=?",Long.class,id);
        Optional<TelegramMember> member=store.memberById(membership);
        if(member.isEmpty() || !member.get().active() || !member.get().aiConsent()) {
            jdbc.update("UPDATE telegram_conversations SET status='CANCELLED',message_text=NULL,voice_file_id=NULL WHERE id=?",id);
            return Optional.empty();
        }
        TelegramMember owner=member.get();
        StringBuilder context=new StringBuilder("Participant timezone: ").append(owner.timezone()).append(". Interests: ").append(owner.interests()).append(". Own daily goals:\n");
        habits.getParticipantHabits(owner.participantId(),ZoneId.of(owner.timezone())).stream()
                .filter(h->store.owns(h.getId(),owner)).limit(10).forEach(h->context.append('#').append(h.getId()).append(' ').append(h.getName()).append("; streak ").append(h.getCurrentStreak()).append(" days\n"));
        context.append("Never claim progress was saved. To record progress, ask the participant to send /done HABIT_ID TOTAL explicitly. Replies must be at most 850 characters so the full transcript fits an audio caption.");
        String lease=UUID.randomUUID().toString();
        jdbc.update("UPDATE telegram_conversations SET status='PROCESSING',attempts=attempts+1,lease_until=?,lease_token=? WHERE id=?",Timestamp.from(clock.instant().plusSeconds(120)),lease,id);
        return jdbc.query("SELECT * FROM telegram_conversations WHERE id=?",(rs,row)->new TelegramConversationJob(id,owner.id(),owner.chatId(),owner.userId(),rs.getLong("message_id"),
                rs.getObject("message_thread_id",Long.class),rs.getString("message_text"),rs.getString("voice_file_id"),context.toString(),owner.responseFormat(),lease),id).stream().findFirst();
    }

    public boolean permitted(TelegramConversationJob job) {
        Integer active=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until>?",Integer.class,job.id(),job.leaseToken(),Timestamp.from(clock.instant()));
        return active==1 && store.memberById(job.membershipId()).filter(m->m.active() && m.aiConsent()).isPresent();
    }

    @Transactional
    public void complete(TelegramConversationJob job,String reply) {
        store.lockIngress();
        Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_conversations WHERE id=? AND status='PROCESSING' AND lease_token=?",Integer.class,job.id(),job.leaseToken());
        if(valid==0) return;
        if(permitted(job)) {
            TelegramUpdate update=new TelegramUpdate(0,job.chatId(),job.userId(),job.messageId(),job.threadId(),"private","","","",false);
            // Full transcript fits Telegram's voice caption; do not silently deliver incomplete text.
            String text=reply==null || reply.isBlank()?"I couldn't prepare a reply. Please try text again or use /help.":reply;
            if(text.length()>850) text=text.substring(0,847)+"…";
            store.reply(update,text,job.format(),"AI_REPLY");
        }
        jdbc.update("UPDATE telegram_conversations SET status='DONE',message_text=NULL,voice_file_id=NULL,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",job.id(),job.leaseToken());
    }
}
