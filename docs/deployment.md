# Deploying the Telegram coach

Deployment preparation is available; this document does not certify production readiness. No hosting provider has been selected or provisioned. The maintainer already has a Telegram bot. Keep its token out of Git, issues, screenshots, and chat messages.

## Container and database

Use a managed container service with a public HTTPS endpoint and a private PostgreSQL 17 database. Run one application replica initially. Put application and database in the same region. Select an always-on service for reminders and queue processing; a sleeping development tier cannot provide reliable scheduling. Confirm the complete monthly budget, including database, backups, traffic and AI/audio usage, before provisioning paid resources.

The Dockerfile builds and tests with the repository Maven wrapper under Java 21, then copies only the executable jar into a Java 21 runtime. The process runs as UID/GID 10001 and takes its port from application configuration. Both official Eclipse Temurin base images are pinned to manifest digests read from Docker Hub on 2 November 2026. For a production release, record the tested application-image digest, scan the image, and retain the previous image for rollback. Review dependency and base-image patches regularly; a pinned image does not update itself.

The runtime also contains FFmpeg with libopus for converting Realtime speech to Telegram voice notes. CI exercises actual encoding/decoding and scans both OS packages and Java dependencies using a pinned Trivy release. The full JSON report is retained for 14 days; fixable HIGH/CRITICAL findings fail the build. Unfixed and lower-severity findings remain visible and require release review. A passing scan gate does not mean the image has no vulnerabilities. FFmpeg is installed from the base distribution's package repository during the build; record the resulting application-image digest for repeatable deployment.

```sh
docker build --pull -t habit-coach:RELEASE_ID .
```

Builds run the fast tests. CI must separately verify migrations and concurrency against PostgreSQL. The database lives outside the container and must have persistent storage and a backup policy. Never store production records or secrets in the application image.

Create a fresh database for the first installation. Flyway applies reviewed versioned migrations at startup and Hibernate validates the result. For an existing Hibernate-managed database, follow [the migration procedure](database-and-progress.md) after taking a backup. Do not enable automatic baselining or schema updates to work around migration failures.

## Secrets and configuration

Set values through the host's secret/environment UI or secrets manager. Use separate staging and production secrets. Generate independent long random values for the internal API key and Telegram webhook secret; neither should reuse the bot token. Set outbound AI-provider quotas and billing alerts in that provider's account.

| Setting | Purpose |
| --- | --- |
| `DB_URL` | JDBC PostgreSQL connection URL; use the host's private endpoint and its required TLS settings |
| `DB_USERNAME`, `DB_PASSWORD` | Dedicated application database credentials |
| `PORT` | Host-assigned HTTP port; application defaults to 8081 |
| `COACH_INTERNAL_API_KEY` | Shared credential for trusted server-to-server REST operations |
| `TELEGRAM_ENABLED=true` | Enable the Telegram webhook and delivery worker |
| `TELEGRAM_BOT_TOKEN` | Existing BotFather token |
| `TELEGRAM_BOT_USERNAME` | Bot username used to recognize addressed commands |
| `TELEGRAM_WEBHOOK_SECRET` | Independent secret supplied when registering the webhook |
| `coach.ai.*` | Explicit provider models, voice, API key and limits; see [coaching configuration](coaching.md) |

`/api` and `/api/**` require exactly one `X-Internal-Api-Key` header matching the configured key. Unset configuration fails closed. These endpoints are administrative server operations, not a participant-facing authentication scheme. Keep them behind network restrictions as additional protection; never place this key in a browser or Telegram message. The Telegram adapter resolves participant ownership from the verified update.

The webhook uses its own `X-Telegram-Bot-Api-Secret-Token` credential. Serve it over HTTPS and register `/telegram/webhook` with Telegram's `setWebhook`, using `secret_token` matching the configured webhook secret. Use a secret-aware script or the host console; do not embed credentials in shell history or print request URLs containing the bot token. Telegram retries updates, so ingress must commit its durable update ledger before acknowledging. Outgoing delivery is at-least-once: a crash after Telegram accepts a send but before the database records success can result in a duplicate reply.

The `.dockerignore` excludes local secrets, keys, logs and build outputs from build context. The Dockerfile copies only the Maven setup and source tree. Keep secret files out of `src/` too.

## Health checks and operation

Application health configuration:

```properties
server.port=${PORT:8081}
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,db
```

Use `GET /actuator/health/liveness` to detect whether the application should be restarted and `GET /actuator/health/readiness` to decide whether it can receive traffic. Readiness includes the database; liveness should not depend on a temporary database failure. Only these paths and the overall health endpoint are public. Other `/actuator` endpoints are denied by the internal API filter even if accidentally exposed through configuration. Keep management paths at their configured defaults unless the filter is updated and retested.

Monitor webhook failure rates, database availability, delivery queue age, permanently failed messages, provider latency/errors, daily AI spend, memory and restarts. Store operational counts and identifiers rather than message bodies, audio or secrets in logs. Configure alert delivery to the maintainer before production launch. Provider outages should degrade to clear text feedback rather than fabricated successful audio or progress updates.

## Release verification

Before calling a release production-ready:

1. Pass local checks, independent review, and CI including PostgreSQL migrations and concurrent check-ins. Review every schema change explicitly.
2. Build and scan the immutable release image. Verify it starts as a non-root user with production-shaped configuration and an isolated staging database.
3. Verify unauthenticated internal requests fail, wrong webhook secrets fail, and health checks disclose no internal details.
4. Exercise real Telegram group onboarding, two participants, another isolated group, preference changes, duplicate updates, leave/rejoin, and opted-in summaries. Confirm real spoken responses, transcripts and incoming voice understanding with the chosen provider.
5. Confirm quiet hours, reminder delivery and retry behavior, per-user limits, queue recovery after a restart, and graceful text fallback during provider failure.
6. Confirm backups, retention and access controls. Restore a backup into a separate database and verify participant/check-in counts and application reads. Record the restore result and time.
7. Deploy a test release, then restore the previous application image and verify it works with the forward-migrated schema. Database changes must be backward-compatible or have an explicitly rehearsed recovery plan; rolling back an image does not reverse Flyway migrations.
8. Complete the maintainer's final merge review, deploy the approved image, register the production webhook, verify live health and a real test check-in, and watch errors and queue age through the initial observation period.

A missing account, credential, unverified provider model, failed restore, or failed live acceptance remains a deployment blocker. Docker is unavailable locally; CI builds the pinned image, starts it against PostgreSQL 17, verifies readiness and internal API authentication, and checks UID 10001. The CI result must pass before release. Hosting remains deliberately undecided by the maintainer as of 2 November 2026; no paid infrastructure or production webhook has been provisioned.

Require the hosting ingress/reverse proxy to enforce a request/body-read timeout in addition to the application's 64 KiB webhook byte cap. Authentication runs before body consumption, and chunked payloads are bounded before MVC deserializes them. This does not replace ingress protection against clients that send validly authenticated bodies extremely slowly.

References: [Docker build guidance](https://docs.docker.com/build/building/best-practices/), [Spring Boot actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Telegram Bot API](https://core.telegram.org/bots/api).
