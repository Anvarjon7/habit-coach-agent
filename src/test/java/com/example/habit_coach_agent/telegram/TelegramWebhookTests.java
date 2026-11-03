package com.example.habit_coach_agent.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelegramWebhookTests {
    private final String secret="abcdefghijklmnopqrstuvwxyz0123456789";
    @Test
    void authenticationPrecedesParsingAndRejectsInvalidUpdates() throws Exception {
        var commands=mock(TelegramCommandService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new TelegramWebhookController(commands,new ObjectMapper(),secret)).build();
        mvc.perform(post("/telegram/webhook").contentType("application/json").content("not json")).andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token",secret).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token",secret).contentType("application/json").content("{\"update_id\":1}")).andExpect(status().isOk());
        verify(commands,times(1)).accept(any());
        mvc.perform(post("/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token",secret).contentType("application/json").content(" ".repeat(65_537))).andExpect(status().isPayloadTooLarge());
    }
    @Test
    void enabledWebhookRequiresStrongSecret() {
        assertThrows(IllegalStateException.class,()->new TelegramWebhookController(mock(TelegramCommandService.class),new ObjectMapper(),"short"));
    }
}
