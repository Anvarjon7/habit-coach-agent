-- Intentionally fail if historical duplicates exist; do not silently discard progress.
ALTER TABLE habit_check_ins ADD CONSTRAINT uq_habit_check_in_date UNIQUE (habit_id, date);
CREATE INDEX ix_habits_participant ON habits(participant_id);
