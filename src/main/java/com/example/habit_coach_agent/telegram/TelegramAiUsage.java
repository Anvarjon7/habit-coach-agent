package com.example.habit_coach_agent.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class TelegramAiUsage {
    private final JdbcTemplate jdbc;
    private final TelegramStore store;
    private final Clock clock;
    private final int speechLimit;
    private final int globalLimit;
    public TelegramAiUsage(JdbcTemplate jdbc,TelegramStore store,Clock clock,
            @Value("${telegram.daily-speech-limit:20}") int speechLimit,
            @Value("${telegram.daily-global-limit:200}") int globalLimit) {
        if(speechLimit<1 || speechLimit>1000 || globalLimit<1 || globalLimit>10000) throw new IllegalStateException("Invalid speech usage limits");
        this.jdbc=jdbc; this.store=store; this.clock=clock; this.speechLimit=speechLimit; this.globalLimit=globalLimit;
    }
    @Transactional
    public boolean reserveSpeech(TelegramOutgoing outgoing) {
        store.lockIngress();
        if(outgoing.recipientUserId()==null || store.member(outgoing.chatId(),outgoing.recipientUserId()).filter(m->m.active() && m.aiConsent()).isEmpty()) return false;
        LocalDate day=LocalDate.now(clock.withZone(ZoneOffset.UTC));
        jdbc.update("DELETE FROM telegram_ai_speech_usage WHERE day_utc<?",day.minusDays(1));
        String userKey;
        try { userKey=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("speech:"+outgoing.recipientUserId()).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
        var used=jdbc.queryForList("SELECT synthesis_requests FROM telegram_ai_speech_usage WHERE day_utc=? AND user_key=?",Integer.class,day,userKey);
        var global=jdbc.queryForList("SELECT accepted_requests FROM telegram_ai_daily_totals WHERE day_utc=?",Integer.class,day);
        if((!used.isEmpty() && used.getFirst()>=speechLimit) || (!global.isEmpty() && global.getFirst()>=globalLimit)) return false;
        if(used.isEmpty()) jdbc.update("INSERT INTO telegram_ai_speech_usage(day_utc,user_key,synthesis_requests) VALUES (?,?,1)",day,userKey);
        else jdbc.update("UPDATE telegram_ai_speech_usage SET synthesis_requests=synthesis_requests+1 WHERE day_utc=? AND user_key=?",day,userKey);
        if(global.isEmpty()) jdbc.update("INSERT INTO telegram_ai_daily_totals(day_utc,accepted_requests) VALUES (?,1)",day);
        else jdbc.update("UPDATE telegram_ai_daily_totals SET accepted_requests=accepted_requests+1 WHERE day_utc=?",day);
        return true;
    }
    @Transactional
    @Scheduled(cron="0 0 0 * * *",zone="UTC")
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupExpired() {
        jdbc.update("DELETE FROM telegram_ai_speech_usage WHERE day_utc<?",LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1));
    }
}
