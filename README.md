# Habit Coach Agent

A Telegram AI coach for participant-chosen daily habits, natural conversation, and optional written or spoken encouragement. There is no product age boundary. Personalization uses stated interests, language, timezone and preferences.

## Implemented foundation

- Java 21 / Spring Boot / PostgreSQL with versioned Flyway migrations.
- Reliable daily progress: absolute totals, date validation, database locking, unique daily entries and history-based streaks.
- Group-specific participant identities, opt-in participation and sharing, authenticated webhook and durable queues.
- Text/audio preferences, read-only coaching provider, incoming OGG voice transcription and Opus speech generation.
- Opt-in reminders, quiet hours, reflection and weekly consistency challenges.
- Internal REST authentication, health checks and a non-root container definition.

This is implementation work under review, not a claim of a deployed production service. Live provider/Telegram acceptance, cost configuration and deployment gates are documented below.

## Quick start

Install Java 21, then start PostgreSQL (for example `docker compose up -d postgres`). Copy `.env.example` to `.env.local` and fill it locally. Do not commit credentials. On Windows, `scripts/run-local.ps1` reads this file without executing its contents. On other systems set the environment variables through your shell or secret manager, then run `./mvnw spring-boot:run`.

Run `./mvnw --batch-mode --no-transfer-progress verify` (Windows: `mvnw.cmd`). Fast tests use isolated H2. CI additionally checks migrations and integration tests on PostgreSQL. Main application uses PostgreSQL and validates the migrated schema; it never silently updates existing tables.

Telegram is disabled until configured. The default coaching provider supplies basic written tips without paid API calls; it explicitly reports that audio is unavailable. Configure supported model IDs, a provider key, usage controls and explicit participant consent before live AI processing.

## Telegram usage

Add the named AI bot to your group. Use `/join`, then `/habit Read one page`. `/progress` lists your habit IDs; `/done HABIT_ID TOTAL` sets today's total. Repeating a total does not add credit.

Use `/format text` or `/format audio`, `/timezone Europe/Warsaw`, and `/interests reading, football` to personalize interaction. `/ai on` opts into sending relevant messages and voice to the configured AI provider; `/ai off` stops future processing. Address the bot explicitly in a group to request conversational coaching. Voice interpretation does not automatically change progress.

`/share on` opts into group summaries; `/summary` shows opted-in members. `/challenge HABIT_ID` joins a weekly challenge, `/leaderboard` shows shared scores, and `/challenge off` leaves. Scores count completed days of one chosen habit, not raw volume.

`/remind HH:mm` opts into daily reminders and Sunday reflections; `/quiet 21:00 08:00` defines quiet hours. `/pause`, `/resume` and `/remind off` control scheduled messages. `/leave` pauses participation; `/forget confirm` deletes the participant's stored data for this chat. Existing Telegram messages and provider retention are governed separately.

## Read before deployment

- [Implementation plan and decisions](docs/implementation-plan.md)
- [Progress semantics and existing-database migration](docs/database-and-progress.md)
- [Telegram configuration, privacy and delivery limits](docs/telegram.md)
- [Text/audio provider configuration and limitations](docs/coaching.md)
- [Reviewed coaching techniques and evidence limits](docs/coaching-evidence.md)
- [Deployment, backup and rollback](docs/deployment.md)
- [Live pilot acceptance and coaching evaluation](docs/pilot-acceptance.md)
- [Reminders, competition, monitoring and retention](docs/engagement-and-operations.md)
- [Contribution and review workflow](CONTRIBUTING.md)

REST `/api/participants` operations are server-to-server and require `X-Internal-Api-Key`. They are not a public client API; the Telegram adapter resolves each sender's membership instead of accepting a caller-supplied participant identity. Shared group replies are visible to everyone in the chat regardless of summary preference.
