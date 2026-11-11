package com.example.habit_coach_agent.coaching;

@FunctionalInterface
interface SpeechGenerator {
    byte[] speak(String text);
}
