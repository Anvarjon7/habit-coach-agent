# Telegram coach implementation plan

Confirmed by the maintainer on 2 November 2026.

The coach serves participants without a product age boundary. Each participant can choose written or spoken coaching. The first release must also understand Telegram voice messages. Use a clearly identified AI bot with a natural, supportive personality.

## Ordered work

| Issue | Outcome | Dependency |
| --- | --- | --- |
| #16 | Reproducible Java 21 build and isolated tests | none |
| #17 | Validated, replay-safe, date-correct progress | #16 |
| #18 | Telegram identities, group memberships, preferences and isolation | #17 |
| #19 | Group onboarding, habit creation, check-in and progress in Telegram | #18 |
| #20 | Personalized conversation using reviewed techniques and domain tools | #19 |
| #21 | Incoming voice transcription and preferred spoken/text replies | #20 |
| #22 | Reminders, weekly reflection and opt-in competition | #19 |
| #23 | Production checks, hosting configuration, deployment and verification | all above |

## Decisions

- Keep Java 21, Spring Boot and PostgreSQL. Build the existing backend rather than replace it.
- Keep identity, access control, progress and scores deterministic. A language model interprets and explains; validated domain services control writes.
- Fast automated tests use an isolated H2 database. This does not substitute for migration/concurrency tests against PostgreSQL before production.
- Do not change system-wide Java settings. Select the JDK per process when necessary.
- The Telegram bot already exists; hosting has not been selected. Hosting and AI provider costs require an explicit choice before provisioning paid resources.
- Store tokens only in local/hosting secrets. Do not paste secrets into issues, code, or chat.
- Explain each milestone: problem, choice and tradeoff, implementation, validation, and remaining limits. Maintain this log as decisions change.

## Production gates

Passing tests and independent review; reviewed migrations; authenticated ingress; group isolation; duplicate update handling; retry-safe delivery; audio fallback; provider quotas; quiet hours; health/metrics; backups and restore verification; secrets configuration; live Telegram acceptance; rollback procedure.

No deployment is considered production-ready until these gates pass. The maintainer retains final merge authority under AGENTS.md.

## Integration review, 3 November 2026

The Telegram journey, scoped membership preferences, durable delivery and conversation queues, text/audio integration, reminder/challenge controls, internal API authentication, retention and portable container preparation are implemented on `feature/telegram-group-coach`. This integration spans #18–#22 and the preparation portion of #23 because consent, queue delivery and scheduling share migrations and transaction boundaries. The provider remains read-only: natural language never silently writes progress.

Independent review identified two command-control gaps: reply throttling could suppress sharing/reminder/challenge opt-outs, and an inactive participant could not delete their retained records. Opt-outs now change state even when their acknowledgement cannot be queued; `/forget` works after `/leave`. Regression tests cover both paths.

The existing packaged application successfully started against the restored local PostgreSQL database, reported readiness UP, and read the restored habit and its check-in through authenticated HTTP. This completes the local restore application-read rehearsal; hosted backups and release rollback remain unverified.

Hosting remains deliberately undecided. Production still requires approved provider configuration and costs, real Telegram/audio acceptance, broader coaching evaluations and reviewed techniques, container CI and image scanning, operational alerts, and a hosted restore/rollback rehearsal. No production webhook or paid infrastructure has been created. Long-term conversation memory, structured developmental personalization, target-based completion and rest-day schedules are not implemented.
