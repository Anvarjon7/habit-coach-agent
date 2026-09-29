package com.example.habit_coach_agent.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class TelegramTeamLeadService {

    private static final Logger log = LoggerFactory.getLogger(TelegramTeamLeadService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String token;
    private final long allowedUserId;
    private final long allowedChatId;
    private final boolean naturalLanguageEnabled;

    private long updateOffset = 0;

    public TelegramTeamLeadService(
            ObjectMapper objectMapper,
            @Value("${TELEGRAM_BOT_TOKEN:}") String token,
            @Value("${TELEGRAM_ALLOWED_USER_ID:0}") long allowedUserId,
            @Value("${TELEGRAM_ALLOWED_CHAT_ID:0}") long allowedChatId,
            @Value("${TELEGRAM_NATURAL_LANGUAGE:false}") boolean naturalLanguageEnabled) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.token = token;
        this.allowedUserId = allowedUserId;
        this.allowedChatId = allowedChatId;
        this.naturalLanguageEnabled = naturalLanguageEnabled;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollUpdates() {
        if (token.isBlank()) {
            log.warn("Telegram Team Lead is disabled: TELEGRAM_BOT_TOKEN is not set.");
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + token
                    + "/getUpdates?timeout=10&limit=20&offset=" + updateOffset;

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("ok").asBoolean(false)) {
                log.warn("Telegram getUpdates failed: {}", root.path("description").asText());
                return;
            }

            for (JsonNode update : root.path("result")) {
                updateOffset = update.path("update_id").asLong() + 1;
                handleUpdate(update);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Telegram polling failed", e);
        }
    }

    void handleUpdate(JsonNode update) throws Exception {
        JsonNode message = update.path("message");
        if (message.isMissingNode() || !message.has("text")) {
            return;
        }

        long chatId = message.path("chat").path("id").asLong();
        long userId = message.path("from").path("id").asLong();
        String text = message.path("text").asText().trim();

        if (allowedChatId != 0 && allowedChatId != chatId) {
            return;
        }

        if (text.equals("/whoami")) {
            sendMessage(chatId, "Your Telegram user ID is: " + userId
                    + "\nChat ID is: " + chatId);
            return;
        }

        if (allowedUserId == 0 || allowedUserId != userId) {
            sendMessage(chatId, "I received your message, but this Team Lead is not configured for your user ID yet. "
                    + "Send /whoami, then set TELEGRAM_ALLOWED_USER_ID.");
            return;
        }

        if (text.equals("/start") || text.equals("/help")) {
            sendMessage(chatId, """
                    🤖 Team Lead is online.

                    Commands:
                    /task <description> — submit an engineering task
                    /whoami — show your Telegram user/chat IDs
                    /help — show this help

                    Stage 1 currently records and acknowledges tasks.
                    GitHub Issue creation and coding-agent delegation come next.
                    """);
            return;
        }

        if (text.startsWith("/task ")) {
            acknowledgeTask(chatId, text.substring(6).trim());
            return;
        }

        if (naturalLanguageEnabled && !text.startsWith("/")) {
            acknowledgeTask(chatId, text);
        }
    }

    private void acknowledgeTask(long chatId, String task) throws Exception {
        if (task.isBlank()) {
            sendMessage(chatId, "Please describe the engineering task after /task.");
            return;
        }

        sendMessage(chatId, """
                📋 Task received by Team Lead.

                %s

                Current workflow:
                1. Team Lead receives task
                2. Next stage: create GitHub Issue
                3. Next stage: assign coding agent
                4. Next stage: PR → agent-Ilon review → CI → human approval

                No code has been changed yet.
                """.formatted(task));
    }

    private void sendMessage(long chatId, String text) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", text);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
