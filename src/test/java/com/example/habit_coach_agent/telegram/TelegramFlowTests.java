package com.example.habit_coach_agent.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="telegram.enabled=false")
class TelegramFlowTests {
    @Autowired TelegramCommandService commands;
    @Autowired TelegramStore store;
    @Autowired TelegramOutbox outbox;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM coach_challenges");
        jdbc.update("DELETE FROM coach_engagement");
        jdbc.update("DELETE FROM telegram_conversations");
        jdbc.update("DELETE FROM telegram_ai_daily_totals");
        jdbc.update("DELETE FROM telegram_ai_speech_usage");
        jdbc.update("DELETE FROM telegram_outbox");
        jdbc.update("DELETE FROM telegram_updates");
        jdbc.update("DELETE FROM habit_check_ins");
        jdbc.update("DELETE FROM telegram_habit_links");
        jdbc.update("DELETE FROM habits");
        jdbc.update("DELETE FROM telegram_memberships");
        jdbc.update("DELETE FROM participants");
        openGate();
    }

    private TelegramUpdate update(long id,long chat,long user,long message,String text) {
        return new TelegramUpdate(id,chat,user,message,null,"supergroup","Person"+user,"",text,false);
    }
    private void send(long id,long chat,long user,long message,String text) { commands.accept(update(id,chat,user,message,text)); }
    private long habit(long member) { return jdbc.queryForObject("SELECT habit_id FROM telegram_habit_links WHERE membership_id=?",Long.class,member); }
    private String lastReply() { return jdbc.queryForObject("SELECT response_text FROM telegram_outbox ORDER BY id DESC LIMIT 1",String.class); }
    private void openGate() { jdbc.update("UPDATE telegram_delivery_gate SET next_send_at=? WHERE id=1",Timestamp.from(Instant.EPOCH)); }

    @Test
    void replayDoesNotDuplicateMembershipHabitOrProgress() {
        send(1,-1,7,1,"/join"); send(1,-1,7,1,"/join");
        send(2,-1,7,2,"/habit Read pages"); send(2,-1,7,2,"/habit Read pages");
        long habit=habit(store.member(-1,7).orElseThrow().id());
        send(3,-1,7,3,"/done "+habit+" 10"); send(3,-1,7,3,"/done "+habit+" 10");
        send(4,-1,7,2,"/done "+habit+" 1"); // Old message cannot overwrite a later total.
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_memberships",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_habit_links",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM habit_check_ins",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT value FROM habit_check_ins",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox",Integer.class));
    }

    @Test
    void identityAndHabitOwnershipAreIsolatedByGroupAndUser() {
        send(1,-1,7,1,"/join"); send(2,-1,7,2,"/habit Private reading");
        long habit=habit(store.member(-1,7).orElseThrow().id());
        send(3,-2,7,1,"/join"); send(4,-2,7,2,"/done "+habit+" 1");
        assertTrue(lastReply().contains("not yours"));
        send(5,-1,8,3,"/join"); send(6,-1,8,4,"/done "+habit+" 1");
        assertTrue(lastReply().contains("not yours"));
        send(7,-2,7,3,"/progress");
        assertFalse(lastReply().contains("Private reading"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM habit_check_ins",Integer.class));
    }

    @Test
    void summariesRequireSharingAndLeaveRevokesParticipation() {
        send(1,-1,7,1,"/join"); send(2,-1,7,2,"/habit Secret goal");
        send(3,-1,8,3,"/join"); send(4,-1,8,4,"/summary");
        assertFalse(lastReply().contains("Secret goal"));
        send(5,-1,7,5,"/share on"); send(6,-1,8,6,"/summary");
        assertTrue(lastReply().contains("Secret goal"));
        send(7,-1,7,7,"/leave"); send(8,-1,8,8,"/summary");
        assertFalse(lastReply().contains("Secret goal"));
        send(9,-1,7,9,"/habit Not allowed");
        assertTrue(lastReply().contains("Join voluntarily"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM habits",Integer.class));
        send(10,-1,7,10,"/join");
        assertFalse(store.member(-1,7).orElseThrow().shareProgress());
    }

    @Test
    void preferencesValidateWithoutAnAgeBoundary() {
        send(1,-1,7,1,"/join"); send(2,-1,7,2,"/format audio");
        send(3,-1,7,3,"/timezone Europe/Warsaw");
        send(4,-1,7,4,"/interests books and football");
        TelegramMember member=store.member(-1,7).orElseThrow();
        assertEquals("audio",member.responseFormat());
        assertEquals("Europe/Warsaw",member.timezone());
        assertEquals("books and football",member.interests());
        send(5,-1,7,5,"/timezone Invalid/Zone");
        assertEquals("Europe/Warsaw",store.member(-1,7).orElseThrow().timezone());
        assertTrue(lastReply().contains("IANA timezone"));
    }

    @Test
    void concurrentDuplicateIsAppliedExactlyOnce() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            var first=executor.submit(()-> { start.await(); commands.accept(update(1,-1,7,1,"/join")); return true; });
            var second=executor.submit(()-> { start.await(); commands.accept(update(1,-1,7,1,"/join")); return true; });
            start.countDown(); assertTrue(first.get(15,TimeUnit.SECONDS)); assertTrue(second.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM participants",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox",Integer.class));
    }

    @Test
    void outboxLeasesRetryAndRejectStaleAcknowledgement() {
        send(1,-1,7,1,"/join");
        TelegramOutgoing first=outbox.claim().orElseThrow();
        assertTrue(outbox.claim().isEmpty());
        jdbc.update("UPDATE telegram_outbox SET lease_until=? WHERE id=?",Timestamp.from(Instant.EPOCH),first.id());
        openGate();
        TelegramOutgoing recovered=outbox.claim().orElseThrow();
        assertNotEquals(first.leaseToken(),recovered.leaseToken());
        outbox.delivered(first);
        assertEquals("SENDING",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE id=?",String.class,first.id()));
        outbox.failed(recovered,new TelegramDeliveryException("RATE_LIMIT",false,60));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE id=?",String.class,first.id()));
        openGate(); assertTrue(outbox.claim().isEmpty());
        jdbc.update("UPDATE telegram_outbox SET available_at=?,attempts=4 WHERE id=?",Timestamp.from(Instant.EPOCH),first.id());
        TelegramOutgoing last=outbox.claim().orElseThrow();
        assertEquals(5,last.attempts());
        outbox.failed(last,new TelegramDeliveryException("NETWORK_FAILURE",false,0));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE id=?",String.class,first.id()));
    }

    @Test
    void optOutsApplyWhenAcknowledgementsAreThrottled() {
        send(1,-1,7,1,"/join"); send(2,-1,7,2,"/habit Read");
        TelegramMember member=store.member(-1,7).orElseThrow();
        send(3,-1,7,3,"/share on"); send(4,-1,7,4,"/remind 18:00");
        send(5,-1,7,5,"/challenge "+habit(member.id()));
        for(int id=6;id<=20;id++) send(id,-1,7,id,"/help");
        assertFalse(store.withinReplyBudget(update(21,-1,7,21,"/pause")));
        send(21,-1,7,21,"/pause");
        assertTrue(jdbc.queryForObject("SELECT paused FROM coach_engagement WHERE membership_id=?",Boolean.class,member.id()));
        send(22,-1,7,22,"/remind off");
        assertNull(jdbc.queryForObject("SELECT reminder_time FROM coach_engagement WHERE membership_id=?",String.class,member.id()));
        send(23,-1,7,23,"/share off");
        assertFalse(store.member(-1,7).orElseThrow().shareProgress());
        send(24,-1,7,24,"/challenge off");
        assertFalse(jdbc.queryForObject("SELECT active FROM coach_challenges WHERE membership_id=?",Boolean.class,member.id()));
        assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox",Integer.class));
    }

    @Test
    void inactiveParticipantCanDeleteRecordsWithoutRejoining() {
        send(1,-1,7,1,"/join"); send(2,-1,7,2,"/habit Read");
        long habit=habit(store.member(-1,7).orElseThrow().id());
        send(3,-1,7,3,"/done "+habit+" 1");
        send(4,-1,7,4,"/remind 18:00"); send(5,-1,7,5,"/challenge "+habit);
        send(6,-1,7,6,"/leave"); send(7,-1,7,7,"/forget");
        assertFalse(store.member(-1,7).orElseThrow().active());
        assertTrue(lastReply().contains("/forget confirm"));
        send(8,-1,7,8,"/forget confirm");
        for(String table:java.util.List.of("participants","telegram_memberships","habits","habit_check_ins","telegram_habit_links","coach_engagement","coach_challenges"))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),table);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox",Integer.class));
        assertTrue(lastReply().contains("have been deleted"));
    }

    @Test
    void irrelevantMessagesDoNotTriggerGroupSpam() {
        send(1,-1,7,1,"hello everyone");
        send(2,-1,7,2,"/join@another_bot");
        commands.accept(new TelegramUpdate(3,-1,7,3,null,"supergroup","Other bot","","/join",true));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox",Integer.class));
    }
}
