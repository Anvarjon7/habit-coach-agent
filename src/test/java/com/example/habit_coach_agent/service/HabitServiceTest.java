package com.example.habit_coach_agent.service;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.HabitCheckIn;
import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.repository.HabitCheckInRepository;
import com.example.habit_coach_agent.repository.HabitRepository;
import com.example.habit_coach_agent.repository.ParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private HabitCheckInRepository habitCheckInRepository;

    private HabitService habitService;
    private Participant participant;
    private Habit habit;

    @BeforeEach
    void setUp() {
        habitService = new HabitService(habitRepository, participantRepository, habitCheckInRepository);

        participant = new Participant();
        participant.setId(1L);
        participant.setFirstName("Anwar");

        habit = new Habit();
        habit.setId(10L);
        habit.setName("Morning Running");
        habit.setParticipant(participant);
        habit.setCurrentStreak(0);
        habit.setLastCheckInDate(null);

        lenient().when(habitRepository.findById(10L)).thenReturn(Optional.of(habit));
        lenient().when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(habitCheckInRepository.save(any(HabitCheckIn.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initialCheckInStartsStreakAtOne() {
        LocalDate today = LocalDate.of(2026, 10, 1);

        HabitCheckIn checkIn = habitService.recordCheckIn(1L, 10L, today, 1);

        assertNotNull(checkIn);
        assertEquals(1, habit.getCurrentStreak());
        assertEquals(today, habit.getLastCheckInDate());
        verify(habitRepository, times(1)).save(habit);
    }

    @Test
    void consecutiveDaysIncrementStreak() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        LocalDate day3 = LocalDate.of(2026, 10, 3);

        habitService.recordCheckIn(1L, 10L, day1, 1);
        assertEquals(1, habit.getCurrentStreak());

        habitService.recordCheckIn(1L, 10L, day2, 1);
        assertEquals(2, habit.getCurrentStreak());
        assertEquals(day2, habit.getLastCheckInDate());

        habitService.recordCheckIn(1L, 10L, day3, 1);
        assertEquals(3, habit.getCurrentStreak());
        assertEquals(day3, habit.getLastCheckInDate());
    }

    @Test
    void sameDayCheckInIsIdempotentForStreak() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);

        habitService.recordCheckIn(1L, 10L, day1, 1);
        assertEquals(1, habit.getCurrentStreak());

        // Check in second time on same day
        habitService.recordCheckIn(1L, 10L, day1, 1);
        assertEquals(1, habit.getCurrentStreak(), "Same-day check-in should not increment streak");
        assertEquals(day1, habit.getLastCheckInDate());
    }

    @Test
    void skippingDaysResetsStreakToOne() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        LocalDate day4 = LocalDate.of(2026, 10, 4); // skipped Oct 3

        habitService.recordCheckIn(1L, 10L, day1, 1);
        habitService.recordCheckIn(1L, 10L, day2, 1);
        assertEquals(2, habit.getCurrentStreak());

        // Check in after gap
        habitService.recordCheckIn(1L, 10L, day4, 1);
        assertEquals(1, habit.getCurrentStreak(), "Skipping a day must reset streak to 1");
        assertEquals(day4, habit.getLastCheckInDate());
    }

    @Test
    void rejectsCheckInWhenHabitBelongsToAnotherParticipant() {
        LocalDate today = LocalDate.of(2026, 10, 1);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> habitService.recordCheckIn(99L, 10L, today, 1));

        assertTrue(exception.getMessage().contains("does not belong to specified participant"));
    }
}
