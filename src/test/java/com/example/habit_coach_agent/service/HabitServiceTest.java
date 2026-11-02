package com.example.habit_coach_agent.service;

import com.example.habit_coach_agent.entity.*;
import com.example.habit_coach_agent.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(HabitServiceTest.FixedTime.class)
class HabitServiceTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), ZoneOffset.UTC); }
    }
    @Autowired HabitService service;
    @Autowired ParticipantRepository participants;
    @Autowired HabitRepository habits;
    @Autowired HabitCheckInRepository checkIns;
    Participant participant;
    Habit habit;
    final LocalDate today = LocalDate.of(2026,11,2);

    @BeforeEach void setup() {
        Participant p = new Participant(); p.setFirstName("Reader"); p.setActive(true);
        participant = participants.save(p);
        Habit h = new Habit(); h.setName("Reading");
        habit = service.createHabit(participant.getId(),h);
    }
    @Test void repeatedCheckInSetsAbsoluteTotal() {
        var first = service.recordCheckIn(participant.getId(),habit.getId(),today,10);
        var second = service.recordCheckIn(participant.getId(),habit.getId(),today,10);
        assertEquals(first.getId(),second.getId());
        assertEquals(10,second.getValue());
        assertEquals(1,service.getHabitCheckIns(participant.getId(),habit.getId()).size());
    }
    @Test void backfillRecalculatesConsecutiveStreak() {
        service.recordCheckIn(participant.getId(),habit.getId(),today.minusDays(2),1);
        service.recordCheckIn(participant.getId(),habit.getId(),today,1);
        assertEquals(1,service.getHabit(participant.getId(),habit.getId()).getCurrentStreak());
        service.recordCheckIn(participant.getId(),habit.getId(),today.minusDays(1),1);
        assertEquals(3,service.getHabit(participant.getId(),habit.getId()).getCurrentStreak());
    }
    @Test void oldHistoryDoesNotShowAnActiveStreak() {
        service.recordCheckIn(participant.getId(),habit.getId(),today.minusDays(4),1);
        var result = service.getHabit(participant.getId(),habit.getId());
        assertEquals(0,result.getCurrentStreak());
        assertEquals(today.minusDays(4),result.getLastCheckInDate());
    }
    @Test void futureDatesAndInvalidValuesAreRejected() {
        assertThrows(ResponseStatusException.class,()->service.recordCheckIn(participant.getId(),habit.getId(),today.plusDays(1),1));
        assertThrows(ResponseStatusException.class,()->service.recordCheckIn(participant.getId(),habit.getId(),today,0));
        assertThrows(ResponseStatusException.class,()->service.recordCheckIn(participant.getId(),habit.getId(),today,-1));
        assertTrue(service.getHabitCheckIns(participant.getId(),habit.getId()).isEmpty());
    }
    @Test void localDayCanBeAheadOfUtc() {
        var checkIn = service.recordCheckIn(participant.getId(),habit.getId(),today.plusDays(1),1,ZoneId.of("Europe/Warsaw"));
        assertEquals(today.plusDays(1),checkIn.getDate());
    }
    @Test void anotherParticipantCannotReadOrWriteHabit() {
        assertThrows(ResponseStatusException.class,()->service.recordCheckIn(-99L,habit.getId(),today,1));
        assertThrows(ResponseStatusException.class,()->service.getHabitCheckIns(-99L,habit.getId()));
    }
    @Test void concurrentDailyReportsDoNotDuplicate() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Void> write = () -> { start.await(); service.recordCheckIn(participant.getId(),habit.getId(),today,10); return null; };
            var a = executor.submit(write); var b = executor.submit(write); start.countDown();
            a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        }
        var entries = service.getHabitCheckIns(participant.getId(),habit.getId());
        assertEquals(1,entries.size()); assertEquals(10,entries.getFirst().getValue());
    }
}
