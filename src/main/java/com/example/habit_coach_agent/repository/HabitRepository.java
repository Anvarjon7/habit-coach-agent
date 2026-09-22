package com.example.habit_coach_agent.repository;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findByParticipantId(Long participantId);
}
