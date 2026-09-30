package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.team.TeamCoordinator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class TelegramTeamLeadServiceTests {

    @Test
    void handlesMessageWithoutText() throws Exception {
        var service = new TelegramTeamLeadService(
                new ObjectMapper(),
                mock(TeamCoordinator.class),
                "",
                123L,
                456L,
                true,
                false);

        JsonNode update = new ObjectMapper().readTree("""
                {"update_id": 1, "message": {"chat": {"id": 456}, "from": {"id": 123}}}
                """);

        assertDoesNotThrow(() -> service.handleUpdate(update));
    }
}
