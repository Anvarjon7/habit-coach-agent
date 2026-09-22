package com.example.habit_coach_agent.service;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.repository.HabitRepository;
import com.example.habit_coach_agent.repository.ParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final ParticipantRepository participantRepository;

    public HabitService(HabitRepository habitRepository, ParticipantRepository participantRepository) {
        this.habitRepository = habitRepository;
        this.participantRepository = participantRepository;
    }

    public Habit createHabit(Long participantId, Habit habit) {

        Participant participant = participantRepository
                .findById(participantId)
                .orElseThrow(() ->
                        new RuntimeException("Participant not found"));

        habit.setParticipant(participant);
        habit.setActive(true);

        return habitRepository.save(habit);
    }

    public List<Habit> getParticipantHabits(Long participantId) {
        return habitRepository.findByParticipantId(participantId);
    }
}
