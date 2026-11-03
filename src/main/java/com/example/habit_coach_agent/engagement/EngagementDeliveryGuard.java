package com.example.habit_coach_agent.engagement;

import com.example.habit_coach_agent.telegram.TelegramOutgoing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;

@Service
public class EngagementDeliveryGuard {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public EngagementDeliveryGuard(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    public boolean allow(TelegramOutgoing outgoing) {
        var messages=jdbc.queryForList("SELECT message_kind,expires_at FROM telegram_outbox WHERE id=? AND status='SENDING' AND lease_token=?",outgoing.id(),outgoing.leaseToken());
        if(messages.isEmpty()) return false;
        var message=messages.getFirst();
        if(!java.util.Set.of("REMINDER","REFLECTION").contains(message.get("MESSAGE_KIND"))) return true;
        var memberships=jdbc.queryForList("""
                SELECT m.active,m.timezone,e.paused,e.reminder_time,e.quiet_start,e.quiet_end
                FROM telegram_memberships m JOIN coach_engagement e ON e.membership_id=m.id
                WHERE m.chat_id=? AND m.user_id=?
                """,outgoing.chatId(),outgoing.recipientUserId());
        OffsetDateTime expiry=jdbc.queryForObject("SELECT expires_at FROM telegram_outbox WHERE id=?",OffsetDateTime.class,outgoing.id());
        if(memberships.isEmpty() || !Boolean.TRUE.equals(memberships.getFirst().get("ACTIVE"))
                || Boolean.TRUE.equals(memberships.getFirst().get("PAUSED"))
                || memberships.getFirst().get("REMINDER_TIME")==null
                || expiry==null || !expiry.toInstant().isAfter(clock.instant())) {
            update(outgoing,"CANCELLED",clock.instant()); return false;
        }
        var member=memberships.getFirst();
        var local=ZonedDateTime.now(clock.withZone(ZoneId.of((String)member.get("TIMEZONE"))));
        var start=LocalTime.parse((String)member.get("QUIET_START"));
        var end=LocalTime.parse((String)member.get("QUIET_END"));
        if(ReminderService.isQuiet(local.toLocalTime(),start,end)) {
            var resume=local.toLocalDate().atTime(end).atZone(local.getZone());
            if(!resume.isAfter(local)) resume=resume.plusDays(1);
            update(outgoing,"PENDING",resume.toInstant()); return false;
        }
        return true;
    }
    private void update(TelegramOutgoing outgoing,String status,Instant available) {
        jdbc.update("UPDATE telegram_outbox SET status=?,available_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",status,Timestamp.from(available),outgoing.id(),outgoing.leaseToken());
    }
}
