CREATE TABLE coach_engagement (
    membership_id BIGINT PRIMARY KEY REFERENCES telegram_memberships(id),
    reminder_time VARCHAR(5),
    quiet_start VARCHAR(5) NOT NULL DEFAULT '21:00',
    quiet_end VARCHAR(5) NOT NULL DEFAULT '08:00',
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    last_reminder_date DATE,
    last_reflection_week DATE
);
ALTER TABLE telegram_outbox ADD COLUMN message_kind VARCHAR(16) NOT NULL DEFAULT 'REPLY';
ALTER TABLE telegram_outbox ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE coach_challenges (
    membership_id BIGINT NOT NULL REFERENCES telegram_memberships(id),
    week_start DATE NOT NULL,
    habit_id BIGINT NOT NULL REFERENCES habits(id),
    joined_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (membership_id, week_start)
);
