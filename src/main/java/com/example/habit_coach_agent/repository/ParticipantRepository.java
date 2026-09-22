package com.example.habit_coach_agent.repository;

import com.example.habit_coach_agent.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
}
