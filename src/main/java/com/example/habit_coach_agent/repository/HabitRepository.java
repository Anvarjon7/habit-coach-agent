package com.example.habit_coach_agent.repository;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findByParticipantId(Long participantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Habit h where h.id = :id")
    Optional<Habit> findForUpdate(@Param("id") Long id);
}
