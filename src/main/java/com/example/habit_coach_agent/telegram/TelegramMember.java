package com.example.habit_coach_agent.telegram;

public record TelegramMember(long id, long chatId, long userId, long participantId,
                             String firstName, boolean active, boolean shareProgress,
                             String responseFormat, String timezone, String interests,
                             long lastMessageId, boolean aiConsent) { }
