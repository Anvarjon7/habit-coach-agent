package com.example.habit_coach_agent.operations;

import com.example.habit_coach_agent.telegram.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RetentionTests {
    @Autowired RetentionService retention;
    @Autowired TelegramCommandService commands;
    @Autowired TelegramStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Test void cleansOldContentButPreservesHabitIdentity() {
        long chat=-900000,user=900000,update=900000;
        commands.accept(new TelegramUpdate(update,chat,user,1,null,"group","Retention test","","/join",false));
        long membership=store.member(chat,user).orElseThrow().id();
        jdbc.update("UPDATE telegram_outbox SET status='SENT',created_at=? WHERE chat_id=?",Timestamp.from(clock.instant().minus(Duration.ofDays(8))),chat);
        jdbc.update("UPDATE telegram_updates SET received_at=? WHERE update_id=?",Timestamp.from(clock.instant().minus(Duration.ofDays(8))),update);
        jdbc.update("INSERT INTO telegram_conversations(membership_id,message_id,message_text,status,created_at) VALUES (?,2,'old secret','PENDING',?)",membership,Timestamp.from(clock.instant().minus(Duration.ofDays(2))));
        LocalDate old=LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(3);
        jdbc.update("INSERT INTO telegram_ai_speech_usage(day_utc,user_key,synthesis_requests) VALUES (?,'retention_test',1)",old);
        retention.clean();
        assertTrue(store.member(chat,user).isPresent());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox WHERE chat_id=?",Integer.class,chat));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_updates WHERE update_id=?",Integer.class,update));
        assertNull(jdbc.queryForObject("SELECT message_text FROM telegram_conversations WHERE membership_id=?",String.class,membership));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM telegram_conversations WHERE membership_id=?",String.class,membership));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_ai_speech_usage WHERE user_key='retention_test'",Integer.class));
    }
}
