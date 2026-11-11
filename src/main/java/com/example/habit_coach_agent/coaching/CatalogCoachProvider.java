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
        if (hasWord(words,"miss|missed|missing|fail|failed|failing|stuck")) {
            suggestion = "A missed task is useful information. Choose an easier next step and a time that fits today. What got in your way?";
        } else if (hasWord(words,"forget|forgetting|forgot|forgotten")) {
            suggestion = "Try a plan you choose: If I finish a familiar routine, then I will do my small action. Which daily routine could be your cue?";
        } else if (hasWord(words,"leaderboard|competition|compare|comparison")) {
            suggestion = "Comparison is optional; your own consistency matters more than another person's total. If a challenge is discouraging, use /challenge off or /share off. Would focusing on your own progress help?";
        } else if (hasWord(words,"read|reading|book|books")) {
            suggestion = "Choose a book you enjoy and a small reading step, such as one page after a daily routine. What time would fit you?";
        } else if (hasWord(words,"language|languages|vocab|vocabulary")) {
            suggestion = "Choose a phrase about something you enjoy and practise it after a familiar daily routine. Which language are you learning?";
        } else {
            suggestion = "Choose one manageable action you care about and a daily cue for trying it. We can reflect and adjust as you go. What would you like to work on?";
        }
        String reply = "I'm your AI habit coach in basic text mode. " + suggestion;
        return reply.substring(0, Math.min(reply.length(), properties.getMaxOutputChars()));
    }

    @Override public byte[] speak(String text) { throw new CoachProviderException(CoachProviderException.Reason.UNAVAILABLE); }
    @Override public String transcribe(byte[] ogg) { throw new CoachProviderException(CoachProviderException.Reason.UNAVAILABLE); }

    private static boolean hasWord(String text, String alternatives) {
        return java.util.regex.Pattern.compile("\\b(?:" + alternatives + ")\\b").matcher(text).find();
    }

    static void checkText(String value, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isBlank())) {
            throw new CoachProviderException(CoachProviderException.Reason.INVALID_INPUT);
        }
    }
}
