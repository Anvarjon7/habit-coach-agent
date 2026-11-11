package com.example.habit_coach_agent.coaching;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BasicCoachingTests {
    private final CatalogCoachProvider coach = new CatalogCoachProvider(new CoachingProperties());

    @Test void addressesReadingSetbackBeforeOfferingAnotherReadingGoal() {
        String reply=coach.reply("I missed my reading again and feel stuck.","");
        assertTrue(reply.contains("easier next step"));
        assertFalse(reply.contains("Choose a book"));
    }

    @Test void linksForgottenPracticeToAParticipantChosenCue() {
        String reply=coach.reply("I forget to practise vocabulary.","");
        assertTrue(reply.contains("If I finish"));
        assertTrue(reply.contains("your cue"));
    }

    @Test void comparisonAdviceIncludesActualOptOutCommands() {
        String reply=coach.reply("The leaderboard makes reading feel like competition.","");
        assertTrue(reply.contains("/challenge off"));
        assertTrue(reply.contains("/share off"));
    }

    @Test void ordinaryWordsAndPositiveRecallDoNotBecomeSetbacks() {
        assertFalse(coach.reply("Do I need permission to join?","").contains("missed task"));
        assertFalse(coach.reply("I remembered my vocabulary today.","").contains("your cue"));
        assertFalse(coach.reply("I already completed it.","").contains("Choose a book"));
    }
}
