package com.example.habit_coach_agent.telegram;

public interface TelegramSender {
    void send(TelegramOutgoing outgoing);
}
