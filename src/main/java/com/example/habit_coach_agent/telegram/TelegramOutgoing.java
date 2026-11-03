package com.example.habit_coach_agent.telegram;

public record TelegramOutgoing(long id, long chatId, Long replyToMessageId, Long recipientUserId,
                               Long threadId, String text, String format, int attempts, String leaseToken) { }
