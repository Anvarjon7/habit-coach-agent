# Database changes and progress semantics

Flyway owns schema changes; Hibernate validates them. Fresh databases run V1 and subsequent migrations automatically. The daily check-in constraint is defense in depth; transactions also take a write lock on the habit before reading/updating progress.

Existing databases created by Hibernate: back up and verify the schema against V1, resolve any duplicate (habit_id,date) entries deliberately, then baseline Flyway at version 1 using an operator-controlled migration step. Do not enable automatic baselining globally. V2 must fail rather than silently discard duplicates. Database changes require explicit review in the PR.

Daily progress is an absolute positive total, not an increment. Missing request/value defaults to one; explicit zero/negative values are rejected. Repeated reports of the same total are safe. A Telegram message/update ledger is additionally required to prevent an old replay replacing a newer total.

Streaks count consecutive completed days ending today or yesterday. Older history shows zero current streak while preserving the last check-in date. Backfilled dates recompute the streak. Future dates are rejected against a supplied participant timezone; internal REST defaults to UTC until called through membership context. This first slice treats any positive daily report as completion; scheduled targets and rest days require a later model extension.

REST endpoints are internal backend operations. They require deployment access control before internet exposure; Telegram ingress resolves the authenticated sender's own membership and never trusts supplied participant IDs.
