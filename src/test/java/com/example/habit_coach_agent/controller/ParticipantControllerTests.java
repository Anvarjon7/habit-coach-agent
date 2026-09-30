package com.example.habit_coach_agent.controller;

import com.example.habit_coach_agent.entity.Participant;
import com.example.habit_coach_agent.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipantController.class)
class ParticipantControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipantService participantService;

    @Test
    void getParticipantByIdReturnsParticipantWhenItExists() throws Exception {
        Participant participant = new Participant();
        participant.setId(1L);
        participant.setTelegramUsername("habit_user");
        participant.setFirstName("Habit");
        participant.setActive(true);
        when(participantService.getParticipantById(1L)).thenReturn(Optional.of(participant));

        mockMvc.perform(get("/api/participants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.telegramUsername").value("habit_user"))
                .andExpect(jsonPath("$.firstName").value("Habit"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getParticipantByIdReturnsNotFoundWhenItDoesNotExist() throws Exception {
        when(participantService.getParticipantById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/participants/99"))
                .andExpect(status().isNotFound());
    }
}
