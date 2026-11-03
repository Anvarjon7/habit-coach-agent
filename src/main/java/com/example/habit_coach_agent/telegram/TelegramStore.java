package com.example.habit_coach_agent.telegram;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Repository
public class TelegramStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final com.example.habit_coach_agent.engagement.EngagementDeliveryGuard deliveryGuard;
    static final RowMapper<TelegramMember> MEMBER = (rs, row) -> new TelegramMember(
            rs.getLong("id"), rs.getLong("chat_id"), rs.getLong("user_id"),
            rs.getLong("participant_id"), rs.getString("first_name"), rs.getBoolean("active"),
            rs.getBoolean("share_progress"), rs.getString("response_format"),
            rs.getString("timezone"), rs.getString("interests"), rs.getLong("last_message_id"),rs.getBoolean("ai_consent"));

    public TelegramStore(JdbcTemplate jdbc, Clock clock, com.example.habit_coach_agent.engagement.EngagementDeliveryGuard deliveryGuard) { this.jdbc = jdbc; this.clock = clock; this.deliveryGuard=deliveryGuard; }

    void lockIngress() { jdbc.queryForObject("SELECT id FROM telegram_ingress_lock WHERE id=1 FOR UPDATE", Integer.class); }
    boolean processed(long updateId) {
        return !jdbc.queryForList("SELECT update_id FROM telegram_updates WHERE update_id=?", Long.class, updateId).isEmpty();
    }
    void markProcessed(long updateId) {
        jdbc.update("INSERT INTO telegram_updates(update_id, received_at) VALUES (?, ?)", updateId, Timestamp.from(clock.instant()));
    }
    public Optional<TelegramMember> member(long chatId, long userId) {
        return jdbc.query("SELECT * FROM telegram_memberships WHERE chat_id=? AND user_id=?", MEMBER, chatId, userId).stream().findFirst();
    }
    public Optional<TelegramMember> memberById(long id) {
        return jdbc.query("SELECT * FROM telegram_memberships WHERE id=?",MEMBER,id).stream().findFirst();
    }
    List<TelegramMember> sharingMembers(long chatId) {
        return jdbc.query("SELECT * FROM telegram_memberships WHERE chat_id=? AND active=TRUE AND share_progress=TRUE ORDER BY id", MEMBER, chatId);
    }
    void join(TelegramUpdate update, long participantId) {
        jdbc.update("""
                INSERT INTO telegram_memberships(chat_id,user_id,participant_id,first_name,active,last_message_id)
                VALUES (?,?,?,?,TRUE,?)
                """, update.chatId(), update.userId(), participantId, plainName(update.firstName()), update.messageId());
    }
    void rejoin(TelegramMember member) {
        jdbc.update("UPDATE telegram_memberships SET active=TRUE, share_progress=FALSE,ai_consent=FALSE WHERE id=?", member.id());
    }
    void advance(TelegramMember member, long messageId) {
        jdbc.update("UPDATE telegram_memberships SET last_message_id=? WHERE id=?", messageId, member.id());
    }
    void leave(TelegramMember member) {
        jdbc.update("UPDATE telegram_memberships SET active=FALSE, share_progress=FALSE,ai_consent=FALSE WHERE id=?", member.id());
        jdbc.update("UPDATE telegram_conversations SET status='CANCELLED',message_text=NULL,voice_file_id=NULL WHERE membership_id=? AND status IN ('PENDING','PROCESSING')",member.id());
        jdbc.update("UPDATE telegram_outbox SET status='CANCELLED' WHERE chat_id=? AND recipient_user_id=? AND status IN ('PENDING','SENDING')", member.chatId(), member.userId());
        cancelSummaries(member.chatId());
    }
    void preference(TelegramMember member, String key, String value) {
        // The column is selected by code, never by message content.
        String sql = switch (key) {
            case "format" -> "UPDATE telegram_memberships SET response_format=? WHERE id=?";
            case "timezone" -> "UPDATE telegram_memberships SET timezone=? WHERE id=?";
            case "interests" -> "UPDATE telegram_memberships SET interests=? WHERE id=?";
            default -> throw new IllegalArgumentException("Unknown preference");
        };
        jdbc.update(sql, value, member.id());
    }
    void share(TelegramMember member, boolean enabled) {
        jdbc.update("UPDATE telegram_memberships SET share_progress=? WHERE id=?", enabled, member.id());
        if(!enabled) cancelSummaries(member.chatId());
    }
    void aiConsent(TelegramMember member,boolean enabled) {
        jdbc.update("UPDATE telegram_memberships SET ai_consent=? WHERE id=?",enabled,member.id());
        if(!enabled) {
            jdbc.update("UPDATE telegram_conversations SET status='CANCELLED',message_text=NULL,voice_file_id=NULL WHERE membership_id=? AND status IN ('PENDING','PROCESSING')",member.id());
            jdbc.update("UPDATE telegram_outbox SET status='CANCELLED' WHERE chat_id=? AND recipient_user_id=? AND (status='PENDING' OR (status='SENDING' AND message_kind='AI_REPLY'))",member.chatId(),member.userId());
        }
    }
    void forget(TelegramMember member) {
        cancelSummaries(member.chatId());
        jdbc.update("DELETE FROM coach_challenges WHERE membership_id=?",member.id());
        jdbc.update("DELETE FROM coach_engagement WHERE membership_id=?",member.id());
        jdbc.update("DELETE FROM telegram_conversations WHERE membership_id=?",member.id());
        jdbc.update("DELETE FROM telegram_outbox WHERE chat_id=? AND recipient_user_id=?",member.chatId(),member.userId());
        jdbc.update("DELETE FROM habit_check_ins WHERE habit_id IN (SELECT id FROM habits WHERE participant_id=?)",member.participantId());
        jdbc.update("DELETE FROM telegram_habit_links WHERE membership_id=?",member.id());
        jdbc.update("DELETE FROM habits WHERE participant_id=?",member.participantId());
        jdbc.update("DELETE FROM telegram_memberships WHERE id=?",member.id());
        jdbc.update("DELETE FROM participants WHERE id=?",member.participantId());
    }
    void link(long habitId, TelegramMember member) {
        jdbc.update("INSERT INTO telegram_habit_links(habit_id,membership_id) VALUES (?,?)", habitId, member.id());
    }
    boolean owns(long habitId, TelegramMember member) {
        return !jdbc.queryForList("SELECT habit_id FROM telegram_habit_links WHERE habit_id=? AND membership_id=?", Long.class, habitId, member.id()).isEmpty();
    }
    public void reply(TelegramUpdate update, String text, String format) {
        reply(update,text,format,"CONTROL");
    }
    public void reply(TelegramUpdate update, String text, String format,String kind) {
        if(!withinReplyBudget(update)) return;
        if (text.length() > 3900) text = text.substring(0, 3900) + "\nMore habits omitted. Choose fewer active goals.";
        jdbc.update("""
                INSERT INTO telegram_outbox(chat_id,reply_to_message_id,recipient_user_id,message_thread_id,response_text,response_format,message_kind,available_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, update.chatId(), update.messageId(), update.userId(), update.threadId(), text, format,
                kind,Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }
    boolean withinReplyBudget(TelegramUpdate update) {
        Integer recent=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox WHERE chat_id=? AND recipient_user_id=? AND created_at>?",Integer.class,update.chatId(),update.userId(),Timestamp.from(clock.instant().minusSeconds(60)));
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox WHERE status IN ('PENDING','SENDING')",Integer.class);
        return recent<20 && pending<10000;
    }
    private void cancelSummaries(long chatId) {
        jdbc.update("UPDATE telegram_outbox SET status='CANCELLED' WHERE chat_id=? AND message_kind='SUMMARY' AND status IN ('PENDING','SENDING')",chatId);
    }
    public boolean sendAllowed(TelegramOutgoing outgoing) {
        List<String> kinds=jdbc.queryForList("SELECT message_kind FROM telegram_outbox WHERE id=? AND status='SENDING' AND lease_token=?",String.class,outgoing.id(),outgoing.leaseToken());
        if(kinds.isEmpty()) return false;
        String kind=kinds.getFirst();
        if(kind.equals("REMINDER") || kind.equals("REFLECTION")) return deliveryGuard.allow(outgoing);
        if(kind.equals("AI_REPLY") || kind.equals("PERSONAL") || kind.equals("SUMMARY")) {
            return outgoing.recipientUserId()!=null && member(outgoing.chatId(),outgoing.recipientUserId())
                    .filter(m->m.active() && (!kind.equals("AI_REPLY") || m.aiConsent())).isPresent();
        }
        return true;
    }
    static String plainName(String name) {
        String clean = name.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replace('@', ' ').trim();
        return clean.isEmpty() ? "Participant" : clean.substring(0, Math.min(100, clean.length()));
    }
}
