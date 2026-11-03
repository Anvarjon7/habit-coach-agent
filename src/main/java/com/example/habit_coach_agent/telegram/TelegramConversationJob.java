package com.example.habit_coach_agent.telegram;

public record TelegramConversationJob(long id,long membershipId,long chatId,long userId,
        long messageId,Long threadId,String text,String voiceFileId,String context,String format,String leaseToken) { }
