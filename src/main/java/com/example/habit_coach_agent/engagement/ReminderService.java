package com.example.habit_coach_agent.engagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class ReminderService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public ReminderService(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    /** Persist schedule marker and outgoing text atomically; actual network delivery is elsewhere. */
    @Transactional
    public void tick() {
        jdbc.queryForObject("SELECT id FROM telegram_ingress_lock WHERE id=1 FOR UPDATE",Integer.class);
        var rows=jdbc.queryForList("""
                SELECT m.id,m.chat_id,m.user_id,m.first_name,m.timezone,m.response_format,
                       e.reminder_time,e.quiet_start,e.quiet_end,e.last_reminder_date,e.last_reflection_week
                FROM telegram_memberships m JOIN coach_engagement e ON e.membership_id=m.id
                WHERE m.active=TRUE AND e.paused=FALSE AND e.reminder_time IS NOT NULL
                """);
        for(var row:rows) {
            var local=ZonedDateTime.now(clock.withZone(ZoneId.of((String)row.get("TIMEZONE"))));
            LocalDate today=local.toLocalDate(); LocalTime now=local.toLocalTime();
            if(isQuiet(now,LocalTime.parse((String)row.get("QUIET_START")),LocalTime.parse((String)row.get("QUIET_END")))) continue;
            LocalDate last=asDate(row.get("LAST_REMINDER_DATE"));
            if(!now.isBefore(LocalTime.parse((String)row.get("REMINDER_TIME"))) && !today.equals(last)) {
                enqueue(row,"A small step for today, "+row.get("FIRST_NAME")+": choose your planned action and its cue. If today is difficult, choose an easier version. /progress shows your goals; /pause silences reminders.","REMINDER",local);
                jdbc.update("UPDATE coach_engagement SET last_reminder_date=? WHERE membership_id=?",today,row.get("ID"));
            }
            LocalDate week=EngagementCommands.week(today);
            if(today.getDayOfWeek()==DayOfWeek.SUNDAY && now.getHour()>=18 && !week.equals(asDate(row.get("LAST_REFLECTION_WEEK")))) {
                enqueue(row,"Weekly reflection for "+row.get("FIRST_NAME")+": what helped you begin, what got in the way, and what small change would make next week easier? Share only what you want this group to see. /pause silences reflections.","REFLECTION",local);
                jdbc.update("UPDATE coach_engagement SET last_reflection_week=? WHERE membership_id=?",week,row.get("ID"));
            }
        }
    }
    static boolean isQuiet(LocalTime time,LocalTime start,LocalTime end) {
        return start.isBefore(end)?!time.isBefore(start)&&time.isBefore(end):!time.isBefore(start)||time.isBefore(end);
    }
    private static LocalDate asDate(Object value) {
        if(value==null) return null;
        if(value instanceof java.sql.Date date) return date.toLocalDate();
        return (LocalDate)value;
    }
    private void enqueue(Map<String,Object> member,String text,String kind,ZonedDateTime local) {
        Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO telegram_outbox(chat_id,recipient_user_id,response_text,response_format,available_at,created_at,message_kind,expires_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,member.get("CHAT_ID"),member.get("USER_ID"),text,member.get("RESPONSE_FORMAT"),now,now,kind,
                Timestamp.from(local.toLocalDate().plusDays(1).atStartOfDay(local.getZone()).toInstant()));
    }
}
