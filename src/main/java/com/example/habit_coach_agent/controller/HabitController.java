package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.HabitCheckIn;
import com.example.habit_coach_agent.service.HabitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/participants/{participantId}/habits")
public class HabitController {
    private final HabitService service;
    public HabitController(HabitService service) { this.service = service; }

    @PostMapping
    public HabitView createHabit(@PathVariable Long participantId, @Valid @RequestBody CreateHabit request) {
        Habit habit = new Habit();
        habit.setName(request.name());
        habit.setDescription(request.description());
        return HabitView.of(service.createHabit(participantId, habit));
    }
    @GetMapping
    public List<HabitView> getParticipantHabits(@PathVariable Long participantId) {
        return service.getParticipantHabits(participantId).stream().map(HabitView::of).toList();
    }
    @GetMapping("/{habitId}")
    public HabitView getHabit(@PathVariable Long participantId, @PathVariable Long habitId) {
        return HabitView.of(service.getHabit(participantId, habitId));
    }
    @PostMapping("/{habitId}/check-ins")
    public CheckInView checkIn(@PathVariable Long participantId, @PathVariable Long habitId,
                              @Valid @RequestBody(required = false) CheckInRequest request) {
        return CheckInView.of(service.recordCheckIn(participantId, habitId,
                request == null ? null : request.date(), request == null || request.value() == null ? 1 : request.value()));
    }
    @GetMapping("/{habitId}/check-ins")
    public List<CheckInView> getCheckIns(@PathVariable Long participantId, @PathVariable Long habitId) {
        return service.getHabitCheckIns(participantId, habitId).stream().map(CheckInView::of).toList();
    }
    public record CreateHabit(@NotBlank @Size(max=255) String name, @Size(max=255) String description) {}
    public record CheckInRequest(LocalDate date, @Min(1) @Max(1000000) Integer value) {}
    public record HabitView(Long id, String name, String description, boolean active, int currentStreak, LocalDate lastCheckInDate) {
        static HabitView of(Habit h) { return new HabitView(h.getId(),h.getName(),h.getDescription(),h.isActive(),h.getCurrentStreak(),h.getLastCheckInDate()); }
    }
    public record CheckInView(Long id, LocalDate date, int value, boolean completed) {
        static CheckInView of(HabitCheckIn c) { return new CheckInView(c.getId(),c.getDate(),c.getValue(),c.isCompleted()); }
    }
}
