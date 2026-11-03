package com.example.habit_coach_agent.telegram;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TelegramOutbox {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public TelegramOutbox(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    public Optional<TelegramOutgoing> claim() {
        Instant now=clock.instant();
        OffsetDateTime next=jdbc.queryForObject("SELECT next_send_at FROM telegram_delivery_gate WHERE id=1 FOR UPDATE",OffsetDateTime.class);
        if(next!=null && next.toInstant().isAfter(now)) return Optional.empty();
        jdbc.update("UPDATE telegram_outbox SET status='FAILED',last_error_code='LEASE_EXHAUSTED',lease_token=NULL WHERE status='SENDING' AND lease_until<=? AND attempts>=5",Timestamp.from(now));
        List<Long> ids=jdbc.queryForList("""
                SELECT id FROM telegram_outbox
                WHERE (status='PENDING' AND available_at<=?) OR (status='SENDING' AND lease_until<=?)
                ORDER BY id LIMIT 1 FOR UPDATE
                """,Long.class,Timestamp.from(now),Timestamp.from(now));
        if(ids.isEmpty()) return Optional.empty();
        long id=ids.getFirst();
        String lease=UUID.randomUUID().toString();
        jdbc.update("UPDATE telegram_outbox SET status='SENDING',attempts=attempts+1,lease_until=?,lease_token=? WHERE id=?",
                Timestamp.from(now.plusSeconds(120)),lease,id);
        // Conservative global gate also respects a single group's 20 messages/minute limit.
        jdbc.update("UPDATE telegram_delivery_gate SET next_send_at=? WHERE id=1",Timestamp.from(now.plusSeconds(3)));
        return jdbc.query("SELECT * FROM telegram_outbox WHERE id=?",(rs,row)->new TelegramOutgoing(
                rs.getLong("id"),rs.getLong("chat_id"),rs.getObject("reply_to_message_id",Long.class),
                rs.getObject("recipient_user_id",Long.class),rs.getObject("message_thread_id",Long.class),
                rs.getString("response_text"),rs.getString("response_format"),rs.getInt("attempts"),rs.getString("lease_token")),id).stream().findFirst();
    }

    @Transactional
    public void delivered(TelegramOutgoing outgoing) {
        jdbc.update("UPDATE telegram_outbox SET status='SENT',lease_until=NULL,lease_token=NULL,last_error_code=NULL WHERE id=? AND status='SENDING' AND lease_token=?",outgoing.id(),outgoing.leaseToken());
    }

    @Transactional
    public void failed(TelegramOutgoing outgoing, TelegramDeliveryException failure) {
        boolean terminal=failure.permanent() || outgoing.attempts()>=5;
        long seconds=Math.max(failure.retryAfterSeconds(),Math.min(3600,5L*(1L<<Math.min(outgoing.attempts(),10))));
        jdbc.update("""
                UPDATE telegram_outbox SET status=?,available_at=?,lease_until=NULL,lease_token=NULL,last_error_code=?
                WHERE id=? AND status='SENDING' AND lease_token=?
                """,terminal?"FAILED":"PENDING",Timestamp.from(clock.instant().plusSeconds(seconds)),failure.getMessage(),outgoing.id(),outgoing.leaseToken());
    }
}
