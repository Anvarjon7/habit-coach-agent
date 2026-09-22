package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.service.HabitService;
import org.springframework.web.bind.annotation.*;

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
}
