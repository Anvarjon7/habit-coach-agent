package com.example.habit_coach_agent.engagement;

import com.example.habit_coach_agent.telegram.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EngagementTests.Time.class)
class EngagementTests {
    @TestConfiguration static class Time {
        @Bean @Primary Clock engagementClock() { return Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired TelegramCommandService commands;
    @Autowired TelegramStore store;
    @Autowired EngagementCommands engagement;
    @Autowired ReminderService reminders;
    @Autowired EngagementDeliveryGuard guard;
    @Autowired JdbcTemplate jdbc;
    static long next=50000;
    long chat,user;
    TelegramMember member;
    @BeforeEach void setup() {
        chat=-(++next); user=++next;
        commands.accept(new TelegramUpdate(++next,chat,user,1,null,"group","Reader","reader","/join",false));
        member=store.member(chat,user).orElseThrow();
    }
    @Test void reminderIsOptInAndOnlyQueuedOncePerLocalDay() {
        reminders.tick();
        assertEquals(0,countReminder());
        engagement.handle(member,"/remind","10:00");
        reminders.tick(); reminders.tick();
        assertEquals(1,countReminder());
    }
    @Test void quietHoursAndPausePreventReminders() {
        engagement.handle(member,"/remind","10:00");
        engagement.handle(member,"/quiet","11:00 13:00");
        reminders.tick(); assertEquals(0,countReminder());
        engagement.handle(member,"/quiet","21:00 08:00");
        engagement.handle(member,"/pause","");
        reminders.tick(); assertEquals(0,countReminder());
        engagement.handle(member,"/resume","");
        reminders.tick(); assertEquals(1,countReminder());
    }
    @Test void challengeDoesNotExposePrivateOrOtherGroupScores() {
        commands.accept(new TelegramUpdate(++next,chat,user,2,null,"group","Reader","reader","/habit Read one page",false));
        long habit=jdbc.queryForObject("SELECT habit_id FROM telegram_habit_links WHERE membership_id=?",Long.class,member.id());
        var response=engagement.handle(member,"/challenge",String.valueOf(habit)).orElseThrow();
        assertTrue(response.contains("You're in"));
        assertTrue(engagement.leaderboard(chat).contains("No shared"));
        jdbc.update("UPDATE telegram_memberships SET share_progress=TRUE WHERE id=?",member.id());
        assertTrue(engagement.leaderboard(chat).contains("Reader: 0%"));
        assertFalse(engagement.leaderboard(chat-1000).contains("Reader:"));
    }
    @Test void challengeRejectsOtherMembersHabitAndCountsDaysNotVolume() {
        commands.accept(new TelegramUpdate(++next,chat,user,2,null,"group","Reader","reader","/habit Read one page",false));
        long habit=jdbc.queryForObject("SELECT habit_id FROM telegram_habit_links WHERE membership_id=?",Long.class,member.id());
        assertTrue(engagement.handle(member,"/challenge","99999999").orElseThrow().contains("own habits"));
        engagement.handle(member,"/challenge",String.valueOf(habit));
        commands.accept(new TelegramUpdate(++next,chat,user,3,null,"group","Reader","reader","/done "+habit+" 1000",false));
        jdbc.update("UPDATE telegram_memberships SET share_progress=TRUE WHERE id=?",member.id());
        assertTrue(engagement.leaderboard(chat).contains("100% (1/1"));
    }
    @Test void overnightQuietWindowWrapsMidnight() {
        assertTrue(ReminderService.isQuiet(LocalTime.of(23,0),LocalTime.of(21,0),LocalTime.of(8,0)));
        assertTrue(ReminderService.isQuiet(LocalTime.of(7,0),LocalTime.of(21,0),LocalTime.of(8,0)));
        assertFalse(ReminderService.isQuiet(LocalTime.of(8,0),LocalTime.of(21,0),LocalTime.of(8,0)));
    }
    @Test void invalidHourProducesUsageInsteadOfAnException() {
        assertTrue(engagement.handle(member,"/remind","24:00").orElseThrow().contains("Use /remind"));
        assertTrue(engagement.handle(member,"/quiet","25:00 08:00").orElseThrow().contains("Use /remind"));
    }
    @Test void pausingCancelsAlreadyQueuedReminders() {
        engagement.handle(member,"/remind","10:00"); reminders.tick();
        engagement.handle(member,"/pause","");
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE chat_id=? AND message_kind='REMINDER'",String.class,chat));
    }
    @Test void delayedDeliveryRechecksQuietHours() {
        engagement.handle(member,"/remind","10:00"); reminders.tick();
        long id=jdbc.queryForObject("SELECT id FROM telegram_outbox WHERE chat_id=? AND message_kind='REMINDER'",Long.class,chat);
        jdbc.update("UPDATE telegram_outbox SET status='SENDING',lease_token='test-lease' WHERE id=?",id);
        jdbc.update("UPDATE telegram_memberships SET timezone='Asia/Tokyo' WHERE id=?",member.id());
        var message=new TelegramOutgoing(id,chat,null,user,null,"Reminder","text",1,"test-lease");
        assertFalse(guard.allow(message));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE id=?",String.class,id));
    }
    @Test void pausingCancelsAlreadyClaimedReminders() {
        engagement.handle(member,"/remind","10:00"); reminders.tick();
        long id=jdbc.queryForObject("SELECT id FROM telegram_outbox WHERE chat_id=? AND message_kind='REMINDER'",Long.class,chat);
        jdbc.update("UPDATE telegram_outbox SET status='SENDING',lease_token='test-lease' WHERE id=?",id);
        engagement.handle(member,"/pause","");
        var message=new TelegramOutgoing(id,chat,null,user,null,"Reminder","audio",1,"test-lease");
        assertFalse(store.sendAllowed(message));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM telegram_outbox WHERE id=?",String.class,id));
    }
    long countReminder() { return jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox WHERE chat_id=? AND response_text LIKE 'A small step%'",Long.class,chat); }
}
