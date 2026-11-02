package com.example.habit_coach_agent.coaching;

import java.util.Locale;

/** Explicit offline mode; supports basic text coaching but never pretends to produce audio. */
public final class CatalogCoachProvider implements CoachProvider {
    private final CoachingProperties properties;
    public CatalogCoachProvider(CoachingProperties properties) { this.properties = properties; }

    @Override
    public String reply(String message, String context) {
        checkText(message, properties.getMaxInputChars(), false);
        checkText(context, properties.getMaxContextChars(), true);
        String words = message.toLowerCase(Locale.ROOT);
        String suggestion;
        if (words.contains("read") || words.contains("book")) {
            suggestion = "Choose a book you enjoy and a small reading step, such as one page after a daily routine. What time would fit you?";
        } else if (words.contains("language") || words.contains("vocab")) {
            suggestion = "Choose a phrase about something you enjoy and practise it after a familiar daily routine. Which language are you learning?";
        } else if (words.contains("miss") || words.contains("fail") || words.contains("stuck")) {
            suggestion = "A missed task is useful information. Choose an easier next step and a time that fits today. What got in your way?";
        } else {
            suggestion = "Choose one manageable action you care about and a daily cue for trying it. We can reflect and adjust as you go. What would you like to work on?";
        }
        String reply = "I'm your AI habit coach in basic text mode. " + suggestion;
        return reply.substring(0, Math.min(reply.length(), properties.getMaxOutputChars()));
    }

    @Override public byte[] speak(String text) { throw new CoachProviderException(CoachProviderException.Reason.UNAVAILABLE); }
    @Override public String transcribe(byte[] ogg) { throw new CoachProviderException(CoachProviderException.Reason.UNAVAILABLE); }

    static void checkText(String value, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isBlank())) {
            throw new CoachProviderException(CoachProviderException.Reason.INVALID_INPUT);
        }
    }
}
