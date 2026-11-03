package com.example.habit_coach_agent.engagement;

import com.example.habit_coach_agent.telegram.TelegramMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class EngagementCommands {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public EngagementCommands(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    public Optional<String> handle(TelegramMember member,String command,String argument) {
        if(!Set.of("/remind","/quiet","/pause","/resume","/challenge","/leaderboard").contains(command)) return Optional.empty();
        ensure(member.id());
        String result;
        try {
            result=switch(command) {
                case "/remind" -> {
                    if(argument.equals("off")) { jdbc.update("UPDATE coach_engagement SET reminder_time=NULL WHERE membership_id=?",member.id()); cancelScheduled(member); yield "Daily reminders and weekly reflections disabled."; }
                    LocalTime time=parseTime(argument);
                    jdbc.update("UPDATE coach_engagement SET reminder_time=?,paused=FALSE WHERE membership_id=?",time.toString(),member.id());
                    yield "Daily reminder and Sunday reflection enabled; daily time "+time+" in "+member.timezone()+". Quiet hours apply; /remind off disables both. These messages appear in this chat.";
                }
                case "/quiet" -> {
                    String[] times=argument.split("\\s+");
                    if(times.length!=2) throw new IllegalArgumentException();
                    LocalTime start=parseTime(times[0]),end=parseTime(times[1]);
                    if(start.equals(end)) yield "Choose different start/end times, for example /quiet 21:00 08:00.";
                    jdbc.update("UPDATE coach_engagement SET quiet_start=?,quiet_end=? WHERE membership_id=?",start.toString(),end.toString(),member.id());
                    yield "Quiet hours saved: "+start+" to "+end+" in your timezone.";
                }
                case "/pause" -> { jdbc.update("UPDATE coach_engagement SET paused=TRUE WHERE membership_id=?",member.id()); cancelScheduled(member); yield "Reminders and weekly reflections paused. /resume restores them."; }
                case "/resume" -> { jdbc.update("UPDATE coach_engagement SET paused=FALSE WHERE membership_id=?",member.id()); yield "Reminder preferences resumed. Choose a daily time with /remind HH:mm."; }
                case "/challenge" -> challenge(member,argument);
                case "/leaderboard" -> leaderboard(member.chatId());
                default -> throw new IllegalArgumentException();
            };
        } catch(IllegalArgumentException invalid) {
            result="Use /remind HH:mm (or off), /quiet HH:mm HH:mm, /challenge HABIT_ID (or off), /leaderboard, /pause or /resume.";
        }
        return Optional.of(result);
    }
    private void ensure(long memberId) {
        if(jdbc.queryForList("SELECT membership_id FROM coach_engagement WHERE membership_id=?",Long.class,memberId).isEmpty())
            jdbc.update("INSERT INTO coach_engagement(membership_id) VALUES (?)",memberId);
    }
    private void cancelScheduled(TelegramMember member) {
        jdbc.update("UPDATE telegram_outbox SET status='CANCELLED' WHERE chat_id=? AND recipient_user_id=? AND message_kind IN ('REMINDER','REFLECTION') AND status IN ('PENDING','SENDING')",member.chatId(),member.userId());
    }
    private String challenge(TelegramMember member,String argument) {
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(member.timezone()))),week=week(today);
        if(argument.equals("off")) {
            jdbc.update("UPDATE coach_challenges SET active=FALSE WHERE membership_id=? AND week_start=?",member.id(),week);
            return "You have left this week's challenge. Your normal habits continue.";
        }
        long habit=Long.parseLong(argument);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM telegram_habit_links WHERE membership_id=? AND habit_id=?",Long.class,member.id(),habit)==0)
            return "Choose one of your own habits from /progress.";
        var existing=jdbc.queryForList("SELECT habit_id FROM coach_challenges WHERE membership_id=? AND week_start=?",Long.class,member.id(),week);
        if(!existing.isEmpty() && existing.getFirst()!=habit) return "Keep one focus habit for this week's challenge; choose another next Monday.";
        if(existing.isEmpty()) jdbc.update("INSERT INTO coach_challenges(membership_id,week_start,habit_id,joined_date) VALUES (?,?,?,?)",member.id(),week,habit,today);
        else jdbc.update("UPDATE coach_challenges SET active=TRUE WHERE membership_id=? AND week_start=?",member.id(),week);
        return "You're in this week's consistency challenge for habit #"+habit+". One completed day earns one credit, regardless of volume. /share on makes your score visible; /challenge off leaves.";
    }
    public String leaderboard(long chatId) {
        var entries=jdbc.query("""
                SELECT m.first_name,m.timezone,c.habit_id,c.joined_date,c.week_start
                FROM coach_challenges c JOIN telegram_memberships m ON m.id=c.membership_id
                WHERE m.chat_id=? AND m.active=TRUE AND m.share_progress=TRUE AND c.active=TRUE
                """,(rs,row)->{
            LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(rs.getString("timezone"))));
            LocalDate start=rs.getObject("joined_date",LocalDate.class);
            if(!week(today).equals(rs.getObject("week_start",LocalDate.class))) return null;
            long days=java.time.temporal.ChronoUnit.DAYS.between(start,today)+1;
            long completed=jdbc.queryForObject("SELECT COUNT(DISTINCT date) FROM habit_check_ins WHERE habit_id=? AND completed=TRUE AND date>=? AND date<=?",Long.class,rs.getLong("habit_id"),start,today);
            return new Score(rs.getString("first_name"),(int)Math.min(100,completed*100/Math.max(1,days)),completed,days);
        },chatId).stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(Score::percent).reversed()).toList();
        if(entries.isEmpty()) return "No shared challenge scores yet. Join with /challenge HABIT_ID, then choose /share on if you want your score shown.";
        StringBuilder result=new StringBuilder("Weekly consistency challenge — personal completion, not effort or ability:\n");
        for(Score score:entries) {
            String line=score.name()+": "+score.percent()+"% ("+score.completed()+"/"+score.days()+" days since joining)\n";
            if(result.length()+line.length()>3500) { result.append("More participants omitted.\n"); break; }
            result.append(line);
        }
        return result.append("Different goals have different difficulty. Celebrate participation; comparisons are only a friendly game.").toString();
    }
    record Score(String name,int percent,long completed,long days) {}
    static LocalTime parseTime(String text) { if(!text.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new IllegalArgumentException(); return LocalTime.parse(text); }
    static LocalDate week(LocalDate day) { return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
}
