package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.HabitCheckIn;
import com.example.habit_coach_agent.service.HabitService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/participants/{participantId}/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public Habit createHabit(@PathVariable Long participantId, @RequestBody Habit habit) {
        return habitService.createHabit(participantId, habit);
    }

    @GetMapping
    public List<Habit> getParticipantHabits(@PathVariable Long participantId) {
        return habitService.getParticipantHabits(participantId);
    }

    @GetMapping("/{habitId}")
    public Habit getHabit(@PathVariable Long participantId, @PathVariable Long habitId) {
        return habitService.getHabit(participantId, habitId);
    }

    @PostMapping("/{habitId}/check-ins")
    public HabitCheckIn checkIn(
            @PathVariable Long participantId,
            @PathVariable Long habitId,
            @RequestBody(required = false) CheckInRequest request) {
        LocalDate date = request != null && request.date() != null ? request.date() : LocalDate.now();
        int value = request != null && request.value() > 0 ? request.value() : 1;
        return habitService.recordCheckIn(participantId, habitId, date, value);
    }

    @GetMapping("/{habitId}/check-ins")
    public List<HabitCheckIn> getCheckIns(
            @PathVariable Long participantId,
            @PathVariable Long habitId) {
        return habitService.getHabitCheckIns(participantId, habitId);
    }

    public record CheckInRequest(LocalDate date, int value) {}
}
