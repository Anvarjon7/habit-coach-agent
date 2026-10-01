package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Habit;
import com.example.habit_coach_agent.entity.HabitCheckIn;
import com.example.habit_coach_agent.service.HabitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HabitController.class)
class HabitControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HabitService habitService;

    @Test
    void getParticipantHabitsReturnsHabitWithStreak() throws Exception {
        Habit habit = new Habit();
        habit.setId(10L);
        habit.setName("Reading 30 mins");
        habit.setCurrentStreak(5);
        habit.setLastCheckInDate(LocalDate.of(2026, 10, 1));
        habit.setActive(true);

        when(habitService.getParticipantHabits(1L)).thenReturn(List.of(habit));

        mockMvc.perform(get("/api/participants/1/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Reading 30 mins"))
                .andExpect(jsonPath("$[0].currentStreak").value(5))
                .andExpect(jsonPath("$[0].lastCheckInDate").value("2026-10-01"));
    }

    @Test
    void checkInRecordsStreakAndReturnsOk() throws Exception {
        HabitCheckIn checkIn = new HabitCheckIn();
        checkIn.setId(100L);
        checkIn.setDate(LocalDate.of(2026, 10, 1));
        checkIn.setValue(1);
        checkIn.setCompleted(true);

        when(habitService.recordCheckIn(eq(1L), eq(10L), any(LocalDate.class), eq(1)))
                .thenReturn(checkIn);

        mockMvc.perform(post("/api/participants/1/habits/10/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-10-01",
                                  "value": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.completed").value(true));
    }
}
