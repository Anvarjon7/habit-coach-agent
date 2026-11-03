package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.service.HabitService;
import com.example.habit_coach_agent.service.ParticipantService;
import com.example.habit_coach_agent.engagement.EngagementCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TelegramCommandService {
    private final TelegramStore store;
    private final ParticipantService participants;
    private final HabitService habits;
    private final Clock clock;
    private final String botUsername;
    private final TelegramConversations conversations;
    private final EngagementCommands engagement;
    private static final String HELP = """
            I'm your AI habit coach. Join voluntarily with /join. Replies in this chat are visible to its members.
            /habit Read 5 pages — create a small daily habit
            /done HABIT_ID TOTAL — set today's total (repeating it won't add progress)
            /progress — show your habits here
            /summary — show only members who opted into sharing
            /share on or /share off — include/exclude your progress in summaries
            /format text or /format audio — choose your response preference
            /timezone Europe/Warsaw — use your local day
            /interests reading, football — save interests for future coaching
            /ai on or /ai off — allow/stop sending your addressed messages, own goals and interests to the AI provider; voice uses transcription
            /remind HH:mm; /quiet HH:mm HH:mm; /pause; /resume — reminder controls
            /challenge HABIT_ID; /leaderboard — optional weekly consistency challenge
            /forget then /forget confirm — delete your stored data in this chat
            /leave — stop participating and sharing
            Start with something achievable, and choose a consistent cue in your day.
            """;

    public TelegramCommandService(TelegramStore store, ParticipantService participants,
            HabitService habits, Clock clock, TelegramConversations conversations,EngagementCommands engagement,
            @Value("${telegram.bot-username:${TELEGRAM_BOT_USERNAME:}}") String botUsername) {
        this.store=store; this.participants=participants; this.habits=habits; this.clock=clock;
        this.conversations=conversations; this.engagement=engagement;
        this.botUsername=botUsername.replace("@", "").toLowerCase(Locale.ROOT);
    }

    /** Commit update identity, state changes and reply together; never call a network here. */
    @Transactional
    public void accept(TelegramUpdate update) {
        store.lockIngress();
        if (store.processed(update.updateId())) return;
        store.markProcessed(update.updateId());
        if (!update.supported() || update.text().length()>4096) return;
        if(!update.text().startsWith("/")) {
            if(addressed(update)) conversations.enqueue(update);
            return;
        }
        String[] parts=update.text().trim().split("\\s+",2);
        String command=parts[0].toLowerCase(Locale.ROOT);
        int at=command.indexOf('@');
        if(at>=0) {
            if (!command.substring(at+1).equals(botUsername) || botUsername.isBlank()) return;
            command=command.substring(0,at);
        }
        String argument=parts.length==2?parts[1].trim():"";
        // Apply opt-outs even when acknowledgement delivery is throttled.
        boolean safety=java.util.Set.of("/leave","/forget","/pause").contains(command)
                || (argument.equals("off") && java.util.Set.of("/ai","/share","/remind","/challenge").contains(command));
        if(!safety && !store.withinReplyBudget(update)) return;
        Optional<TelegramMember> existing=store.member(update.chatId(),update.userId());
        if(existing.isPresent() && update.messageId()<=existing.get().lastMessageId()) return;
        existing.ifPresent(member -> store.advance(member,update.messageId()));
        if(command.equals("/join")) {
            if(existing.isEmpty()) {
                Participant participant=new Participant();
                participant.setFirstName(TelegramStore.plainName(update.firstName()));
                participant.setTelegramUsername(update.username().substring(0,Math.min(255,update.username().length())));
                participant=participants.createParticipant(participant);
                store.join(update,participant.getId());
            } else if(!existing.get().active()) store.rejoin(existing.get());
            store.reply(update,"You're in! I'm an AI coach. Choose one achievable daily action with /habit NAME. Your progress is excluded from group summaries unless you choose /share on. Messages you ask me to post here are visible to the group. /help lists controls.","text");
            return;
        }
        if(command.equals("/help") || command.equals("/start")) { store.reply(update,HELP,"text"); return; }
        if(existing.isEmpty() || (!existing.get().active() && !command.equals("/forget"))) {
            store.reply(update,"Join voluntarily with /join before setting goals or recording progress. /help explains what I store and share.","text");
            return;
        }
        TelegramMember member=existing.get();
        Optional<String> engagementReply=engagement.handle(member,command,argument);
        if(engagementReply.isPresent()) {
            store.reply(update,engagementReply.get(),command.equals("/leaderboard")?"text":member.responseFormat(),command.equals("/leaderboard")?"SUMMARY":"CONTROL");
            return;
        }
        String response;
        switch(command) {
            case "/ai" -> {
                if(!argument.equals("on") && !argument.equals("off")) { response="Use /ai on to allow provider processing of addressed messages, voice, your goals and interests, or /ai off to stop. Replies here are visible to the group."; break; }
                store.aiConsent(member,argument.equals("on"));
                response=argument.equals("on")?"AI coaching enabled. Address me directly here or reply to one of my messages. Your addressed messages, voice, own goals and interests may be processed by the configured AI provider. Voice is transcribed; AI replies never record progress automatically. /ai off stops future processing.":"AI processing stopped and queued requests cancelled. Habit commands still work. Previously transmitted data follows the provider's retention policy.";
            }
            case "/forget" -> {
                if(!argument.equals("confirm")) { response="Use /forget confirm to delete your profile, preferences, habits, check-ins, challenges, reminders and queued messages in this chat. Other chats and existing Telegram messages remain unchanged."; break; }
                store.forget(member);
                response="Your participant profile and progress in this chat have been deleted. Content-free speech abuse-prevention counters remain for up to two UTC days. Existing Telegram messages and provider retention are separate. Use /join only if you want to start again.";
            }
            case "/habit" -> {
                if(argument.isBlank() || argument.length()>120) { response="Use /habit NAME with a name up to 120 characters."; break; }
                if(ownedHabits(member).size()>=10) { response="You already have 10 goals here. Focus on those before adding another."; break; }
                Habit habit=new Habit(); habit.setName(argument); habit.setDescription("Daily action chosen in Telegram");
                habit=habits.createHabit(member.participantId(),habit);
                store.link(habit.getId(),member);
                response="Your habit #"+habit.getId()+": "+argument+". Choose a daily cue, then report /done "+habit.getId()+" 1 when you complete it.";
            }
            case "/done" -> response=done(member,argument);
            case "/progress" -> response=progress(member);
            case "/summary" -> response=summary(member.chatId());
            case "/share" -> {
                if(!argument.equals("on") && !argument.equals("off")) { response="Use /share on or /share off."; break; }
                boolean enabled=argument.equals("on"); store.share(member,enabled);
                response=enabled?"Your habit names and streaks may now appear in group summaries. Use /share off any time.":"Your progress is excluded from future group summaries. Existing group messages remain in Telegram.";
            }
            case "/format" -> {
                if(!argument.equals("text") && !argument.equals("audio")) { response="Use /format text or /format audio."; break; }
                store.preference(member,"format",argument);
                response="Preference saved: "+argument+". Text remains available if audio is unavailable.";
            }
            case "/timezone" -> {
                try {
                    if(argument.length()>100) throw new DateTimeException("too long");
                    ZoneId.of(argument); store.preference(member,"timezone",argument);
                    response="Timezone saved. Future check-ins use your local day; existing dated records stay on their recorded date.";
                } catch(DateTimeException invalid) { response="Use an IANA timezone such as /timezone Europe/Warsaw."; }
            }
            case "/interests" -> {
                if(argument.length()>500) { response="Keep interests under 500 characters."; break; }
                store.preference(member,"interests",argument);
                response="Interests saved for coaching. Avoid sharing sensitive information in group messages.";
            }
            case "/leave" -> {
                store.leave(member);
                response="Participation and sharing are paused. Your existing records remain stored; /join can resume. Existing Telegram messages are unchanged.";
            }
            default -> response="I don't recognize that command. Use /help for the available controls.";
        }
        // Shared summaries are always written; individual preferences never dictate a group's format.
        String kind=command.equals("/summary")?"SUMMARY":java.util.Set.of("/progress","/habit","/done").contains(command)?"PERSONAL":"CONTROL";
        store.reply(update,response,command.equals("/summary")?"text":member.responseFormat(),kind);
    }

    private boolean addressed(TelegramUpdate update) {
        if(update.chatType().equals("private")) return true;
        if(botUsername.isBlank()) return false;
        if(update.replyBotUsername().equalsIgnoreCase(botUsername)) return true;
        return java.util.regex.Pattern.compile("(?i)(?<![A-Za-z0-9_])@"+java.util.regex.Pattern.quote(botUsername)+"(?![A-Za-z0-9_])").matcher(update.text()).find();
    }

    private String done(TelegramMember member,String argument) {
        String[] values=argument.split("\\s+");
        if(values.length!=2) return "Use /done HABIT_ID TOTAL, for example /done 3 1.";
        long habitId; int value;
        try { habitId=Long.parseLong(values[0]); value=Integer.parseInt(values[1]); }
        catch(NumberFormatException invalid) { return "Habit ID and total must be whole numbers."; }
        if(habitId<1 || value<1 || value>1_000_000) return "Use a valid habit ID and a total from 1 to 1000000.";
        if(!store.owns(habitId,member)) return "That habit is not yours in this chat. Use /progress for your habits.";
        ZoneId zone=ZoneId.of(member.timezone());
        habits.recordCheckIn(member.participantId(),habitId,LocalDate.now(clock.withZone(zone)),value,zone);
        return "Recorded today's total as "+value+" for habit #"+habitId+". Well done for showing up. Repeating this total won't add progress.";
    }

    private List<Habit> ownedHabits(TelegramMember member) {
        return habits.getParticipantHabits(member.participantId(),ZoneId.of(member.timezone())).stream()
                .filter(h -> store.owns(h.getId(),member)).toList();
    }
    public String progress(TelegramMember member) {
        List<Habit> owned=ownedHabits(member);
        if(owned.isEmpty()) return "No habits yet. Choose a small daily action with /habit NAME.";
        StringBuilder text=new StringBuilder("Your habits in this chat:\n");
        for(Habit habit:owned) text.append('#').append(habit.getId()).append(" ").append(habit.getName())
                .append(" — streak: ").append(habit.getCurrentStreak()).append(" days\n");
        return text.toString();
    }
    private String summary(long chatId) {
        List<TelegramMember> members=store.sharingMembers(chatId);
        if(members.isEmpty()) return "No members have opted into group summaries. Each person can choose /share on.";
        StringBuilder text=new StringBuilder("Opted-in group progress (daily consistency, not a ranking):\n");
        for(TelegramMember member:members) {
            text.append(member.firstName()).append(":\n");
            for(Habit habit:ownedHabits(member)) text.append("• ").append(habit.getName()).append(" — ").append(habit.getCurrentStreak()).append(" days\n");
            if(text.length()>3600) { text.append("More participants omitted to keep this summary short.\n"); break; }
        }
        return text.toString();
    }
}
