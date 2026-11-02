package com.example.habit_coach_agent.service;

import com.example.habit_coach_agent.entity.*;
import com.example.habit_coach_agent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class HabitService {
    private final HabitRepository habits;
    private final ParticipantRepository participants;
    private final HabitCheckInRepository checkIns;
    private final Clock clock;

    public HabitService(HabitRepository habits, ParticipantRepository participants,
                        HabitCheckInRepository checkIns, Clock clock) {
        this.habits = habits;
        this.participants = participants;
        this.checkIns = checkIns;
        this.clock = clock;
    }

    @Transactional
    public Habit createHabit(Long participantId, Habit habit) {
        Participant participant = participants.findById(participantId)
                .orElseThrow(() -> missing("Participant not found"));
        if (habit.getName() == null || habit.getName().isBlank() || habit.getName().length() > 255)
            throw invalid("Habit name must contain 1 to 255 characters");
        if (habit.getDescription() != null && habit.getDescription().length() > 255)
            throw invalid("Habit description must not exceed 255 characters");
        habit.setId(null);
        habit.setParticipant(participant);
        habit.setActive(true);
        habit.setCurrentStreak(0);
        habit.setLastCheckInDate(null);
        return habits.save(habit);
    }

    @Transactional(readOnly = true)
    public List<Habit> getParticipantHabits(Long participantId) {
        return getParticipantHabits(participantId, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public List<Habit> getParticipantHabits(Long participantId, ZoneId zone) {
        List<Habit> result = habits.findByParticipantId(participantId);
        result.forEach(h -> refreshStreak(h, LocalDate.now(clock.withZone(zone))));
        return result;
    }

    @Transactional(readOnly = true)
    public Habit getHabit(Long participantId, Long habitId) {
        return getHabit(participantId, habitId, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public Habit getHabit(Long participantId, Long habitId, ZoneId zone) {
        Habit habit = owned(habits.findById(habitId), participantId);
        refreshStreak(habit, LocalDate.now(clock.withZone(zone)));
        return habit;
    }

    @Transactional
    public HabitCheckIn recordCheckIn(Long participantId, Long habitId, LocalDate date, int value) {
        return recordCheckIn(participantId, habitId, date, value, ZoneOffset.UTC);
    }

    @Transactional
    public HabitCheckIn recordCheckIn(Long participantId, Long habitId, LocalDate date, int value, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate effectiveDate = date == null ? today : date;
        if (effectiveDate.isAfter(today)) throw invalid("Future check-ins are not allowed");
        if (value < 1 || value > 1000000) throw invalid("Progress must be between 1 and 1000000");
        Habit habit = owned(habits.findForUpdate(habitId), participantId);
        if (!habit.isActive()) throw invalid("Habit is paused");
        HabitCheckIn entry = checkIns.findByHabitIdAndDate(habitId, effectiveDate).orElseGet(HabitCheckIn::new);
        entry.setHabit(habit);
        entry.setDate(effectiveDate);
        // Absolute daily total: retries must not increment progress.
        entry.setValue(value);
        entry.setCompleted(true);
        checkIns.saveAndFlush(entry);
        refreshStreak(habit, today);
        habits.save(habit);
        return entry;
    }

    @Transactional(readOnly = true)
    public List<HabitCheckIn> getHabitCheckIns(Long participantId, Long habitId) {
        owned(habits.findById(habitId), participantId);
        return checkIns.findByHabitIdOrderByDateDesc(habitId);
    }

    private Habit owned(Optional<Habit> candidate, Long participantId) {
        Habit habit = candidate.orElseThrow(() -> missing("Habit not found"));
        if (habit.getParticipant() == null || !Objects.equals(habit.getParticipant().getId(), participantId))
            throw missing("Habit not found");
        return habit;
    }

    private void refreshStreak(Habit habit, LocalDate today) {
        List<LocalDate> dates = checkIns.findByHabitIdOrderByDateDesc(habit.getId()).stream()
                .filter(HabitCheckIn::isCompleted).map(HabitCheckIn::getDate)
                .filter(d -> d != null && !d.isAfter(today)).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        habit.setLastCheckInDate(dates.isEmpty() ? null : dates.getFirst());
        int streak = 0;
        if (!dates.isEmpty() && !dates.getFirst().isBefore(today.minusDays(1))) {
            LocalDate expected = dates.getFirst();
            for (LocalDate d : dates) {
                if (!d.equals(expected)) break;
                streak++;
                expected = expected.minusDays(1);
            }
        }
        habit.setCurrentStreak(streak);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
