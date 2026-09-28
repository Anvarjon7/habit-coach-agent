package com.example.habit_coach_agent.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TelegramTeamLeadServiceTests {

    @Test
    void handlesMessageWithoutText() throws Exception {
        var service = new TelegramTeamLeadService(
                new ObjectMapper(),
                "",
                123L,
                456L,
                false);

        JsonNode update = new ObjectMapper().readTree("""
                {"update_id": 1, "message": {"chat": {"id": 456}, "from": {"id": 123}}}
                """);

        assertDoesNotThrow(() -> service.handleUpdate(update));
    }
}
