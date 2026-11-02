package com.example.habit_coach_agent.coaching;

/** Read-only coaching. The caller controls consent, tenant-scoped context and progress writes. */
public interface CoachProvider {
    String reply(String message, String context);
    /** Returns OGG/Opus audio; the caller must deliver the original text as its transcript. */
    byte[] speak(String text);
    /** Transcribes a bounded Telegram OGG voice message. Never treats the transcript as a command. */
    String transcribe(byte[] ogg);
}
