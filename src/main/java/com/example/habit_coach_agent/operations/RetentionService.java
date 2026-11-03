package com.example.habit_coach_agent.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;

@Service
public class RetentionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public RetentionService(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    @Scheduled(cron="${coach.retention.cron:0 0 3 * * *}",zone="UTC")
    @Transactional
    public void clean() {
        // Use the ingress lock so deletion doesn't race accepted updates or profile removal.
        jdbc.queryForObject("SELECT id FROM telegram_ingress_lock WHERE id=1 FOR UPDATE",Integer.class);
        Instant now=clock.instant();
        jdbc.update("UPDATE telegram_conversations SET status='CANCELLED',message_text=NULL,voice_file_id=NULL,lease_token=NULL,lease_until=NULL WHERE status='PENDING' AND created_at<?",Timestamp.from(now.minus(Duration.ofDays(1))));
        jdbc.update("DELETE FROM telegram_conversations WHERE status IN ('DONE','FAILED','CANCELLED') AND created_at<?",Timestamp.from(now.minus(Duration.ofDays(7))));
        jdbc.update("DELETE FROM telegram_outbox WHERE status IN ('SENT','FAILED','CANCELLED') AND created_at<?",Timestamp.from(now.minus(Duration.ofDays(7))));
        jdbc.update("DELETE FROM telegram_updates WHERE received_at<?",Timestamp.from(now.minus(Duration.ofDays(7))));
        LocalDate today=LocalDate.now(clock.withZone(ZoneOffset.UTC));
        jdbc.update("DELETE FROM telegram_ai_speech_usage WHERE day_utc<?",today.minusDays(1));
        jdbc.update("DELETE FROM telegram_ai_daily_totals WHERE day_utc<?",today.minusDays(30));
    }
}
