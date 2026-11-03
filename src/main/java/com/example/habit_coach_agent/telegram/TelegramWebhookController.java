package com.example.habit_coach_agent.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramWebhookController {
    private final TelegramCommandService commands;
    private final ObjectMapper mapper;
    private final byte[] secret;

    public TelegramWebhookController(TelegramCommandService commands, ObjectMapper mapper,
            @Value("${telegram.webhook-secret:${TELEGRAM_WEBHOOK_SECRET:}}") String secret) {
        if (secret.length() < 32 || !secret.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalStateException("Telegram webhook requires a 32-256 character secret");
        this.commands = commands;
        this.mapper = mapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(path = "/telegram/webhook", consumes = "application/json")
    public Map<String, Boolean> receive(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", defaultValue = "") String provided,
            @RequestBody String body) {
        if (provided.length() > 256 || !MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook authentication");
        if (body.length() > 65_536)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Update is too large");
        TelegramUpdate update;
        try {
            update = TelegramUpdate.from(mapper.readTree(body));
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Telegram update");
        }
        commands.accept(update);
        return Map.of("ok", true);
    }
}
