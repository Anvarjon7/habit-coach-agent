package com.example.habit_coach_agent.service;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.HabitCheckIn;
import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.repository.HabitCheckInRepository;
import com.example.habit_coach_agent.repository.HabitRepository;
import com.example.habit_coach_agent.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final ParticipantRepository participantRepository;
    private final HabitCheckInRepository habitCheckInRepository;

    public HabitService(
            HabitRepository habitRepository,
            ParticipantRepository participantRepository,
            HabitCheckInRepository habitCheckInRepository) {
        this.habitRepository = habitRepository;
        this.participantRepository = participantRepository;
        this.habitCheckInRepository = habitCheckInRepository;
    }

    public Habit createHabit(Long participantId, Habit habit) {
        Participant participant = participantRepository
                .findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        habit.setParticipant(participant);
        habit.setActive(true);
        habit.setCurrentStreak(0);
        habit.setLastCheckInDate(null);

        return habitRepository.save(habit);
    }

    public List<Habit> getParticipantHabits(Long participantId) {
        return habitRepository.findByParticipantId(participantId);
    }

    public Habit getHabit(Long participantId, Long habitId) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new RuntimeException("Habit not found"));

        if (habit.getParticipant() == null || !habit.getParticipant().getId().equals(participantId)) {
            throw new RuntimeException("Habit does not belong to specified participant");
        }
        return habit;
    }

    @Transactional
    public HabitCheckIn recordCheckIn(Long participantId, Long habitId, LocalDate checkInDate, int value) {
        LocalDate effectiveDate = checkInDate != null ? checkInDate : LocalDate.now();
        int effectiveValue = Math.max(value, 1);

        Habit habit = getHabit(participantId, habitId);

        // Calculate and update streak
        LocalDate lastDate = habit.getLastCheckInDate();
        if (lastDate == null) {
            habit.setCurrentStreak(1);
            habit.setLastCheckInDate(effectiveDate);
        } else if (effectiveDate.isEqual(lastDate)) {
            // Same day check-in: streak remains unchanged (idempotent)
        } else if (effectiveDate.isEqual(lastDate.plusDays(1))) {
            // Consecutive day: increment streak
            habit.setCurrentStreak(habit.getCurrentStreak() + 1);
            habit.setLastCheckInDate(effectiveDate);
        } else if (effectiveDate.isAfter(lastDate.plusDays(1))) {
            // Skipped one or more days: reset streak to 1
            habit.setCurrentStreak(1);
            habit.setLastCheckInDate(effectiveDate);
        }

        habitRepository.save(habit);

        // Record the check-in entity
        HabitCheckIn checkIn = habitCheckInRepository
                .findByHabitIdAndDate(habit.getId(), effectiveDate)
                .orElseGet(() -> {
                    HabitCheckIn newEntry = new HabitCheckIn();
                    newEntry.setHabit(habit);
                    newEntry.setDate(effectiveDate);
                    return newEntry;
                });

        checkIn.setValue(checkIn.getValue() + effectiveValue);
        checkIn.setCompleted(true);

        return habitCheckInRepository.save(checkIn);
    }

    public List<HabitCheckIn> getHabitCheckIns(Long participantId, Long habitId) {
        Habit habit = getHabit(participantId, habitId);
        return habitCheckInRepository.findByHabitIdOrderByDateDesc(habit.getId());
    }
}
