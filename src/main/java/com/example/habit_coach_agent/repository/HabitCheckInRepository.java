package com.example.habit_coach_agent.repository;

import com.example.habit_coach_agent.entity.HabitCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, Long> {

}
