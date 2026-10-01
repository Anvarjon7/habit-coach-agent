package com.example.habit_coach_agent.repository;

import com.example.habit_coach_agent.entity.HabitCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, Long> {

    List<HabitCheckIn> findByHabitIdOrderByDateDesc(Long habitId);

    Optional<HabitCheckIn> findByHabitIdAndDate(Long habitId, LocalDate date);
}
