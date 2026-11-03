package com.example.habit_coach_agent.engagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="telegram.enabled",havingValue="true")
public class ReminderWorker {
    private final ReminderService reminders;
    public ReminderWorker(ReminderService reminders) { this.reminders=reminders; }
    @Scheduled(fixedDelayString="${coach.reminders.poll-delay-ms:60000}")
    public void tick() { reminders.tick(); }
}
