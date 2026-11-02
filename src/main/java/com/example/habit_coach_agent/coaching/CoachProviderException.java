package com.example.habit_coach_agent.coaching;

/** Deliberately excludes provider bodies, credentials and participant content. */
public class CoachProviderException extends RuntimeException {
    public enum Reason { UNAVAILABLE, INVALID_INPUT, TIMEOUT, BUSY, PROVIDER_ERROR }
    private final Reason reason;

    public CoachProviderException(Reason reason) {
        super(switch (reason) {
            case UNAVAILABLE -> "This coaching format is currently unavailable.";
            case INVALID_INPUT -> "The coaching input is empty, invalid, or exceeds the configured limit.";
            case TIMEOUT -> "The coaching provider timed out. Please try again later.";
            case BUSY -> "The coaching provider is busy. Please try again later.";
            case PROVIDER_ERROR -> "The coaching provider could not complete this request.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
