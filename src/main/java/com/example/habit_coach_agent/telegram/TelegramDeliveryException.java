package com.example.habit_coach_agent.telegram;

/** Safe classification only: upstream bodies and credential-bearing URLs are never retained. */
public class TelegramDeliveryException extends RuntimeException {
    private final boolean permanent;
    private final int retryAfterSeconds;
    public TelegramDeliveryException(String code, boolean permanent, int retryAfterSeconds) {
        super(code); this.permanent=permanent; this.retryAfterSeconds=retryAfterSeconds;
    }
    public boolean permanent() { return permanent; }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}
