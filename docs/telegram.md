# Telegram coaching integration

## Identity, visibility and opt-in

The product uses the Telegram Bot API with a clearly identified AI coach. No age boundary is enforced. Each `(chat_id, user_id)` membership has its own participant, goals and preferences, so using the same Telegram profile in another group does not expose its goals. Habit ownership is checked against a membership link before any write.

`/join` voluntarily enrolls a participant. Nothing appears in shared progress summaries until `/share on`; `/share off`, `/leave` and `/forget confirm` cancel queued summaries that may contain that participant's progress. The user can still explicitly request `/progress` in the group: replies there are visible to the group. This is not a private-message interface inside a group.

`/ai on` separately permits sending addressed messages, voice, own goals and interests to the configured AI provider. Provider retention terms apply. `/ai off` cancels queued provider work and queued AI replies. A format preference alone does not give provider consent. `/leave` disables participation, sharing and AI processing. `/forget confirm` deletes the caller's profile, progress, challenges, reminder preferences and queued messages only in the current chat. Telegram's existing messages and provider-side retention are separate.

Speech abuse-prevention counters contain a pseudonymous hash of a Telegram user ID, a UTC day and a count, and are retained for the current and previous UTC dates. They survive profile deletion to prevent quota resets; no message/audio is retained in these counters. The hash is pseudonymous, not anonymous. Anonymous global daily operation totals also survive deletion.

## Configuration

The webhook and workers are disabled unless `telegram.enabled=true`. Configure these server-side secrets/settings:

- `TELEGRAM_BOT_TOKEN`: existing BotFather token; never commit it or paste it in chat.
- `TELEGRAM_WEBHOOK_SECRET`: 32–256 characters from `A-Z`, `a-z`, `0-9`, `_`, `-`; register the same value as `setWebhook.secret_token`.
- `TELEGRAM_BOT_USERNAME`: the configured bot's username, without `@`; used for addressed group conversation and suffixed commands.
- `telegram.enabled=true`: enables authenticated `/telegram/webhook` and workers. In production prefer environment `TELEGRAM_ENABLED=true`.
- Configure the AI provider separately as described in `docs/coaching.md`.

Register an HTTPS webhook URL ending in `/telegram/webhook` with Telegram's `setWebhook`; request ordinary `message` updates only. Do not run another poller for the same bot. A bot with Telegram's default group privacy mode receives commands and replies to it. Ambient group reading is not needed for this implementation. For voice coaching in a group, reply with a voice note to the bot's message; in private chat, send the voice note directly.

Official API: https://core.telegram.org/bots/api. Group privacy configuration: https://core.telegram.org/bots/features.

## Available commands

`/help`, `/join`, `/habit NAME`, `/done HABIT_ID TOTAL`, `/progress`, `/summary`, `/share on|off`, `/format text|audio`, `/timezone Europe/Warsaw`, `/interests TEXT`, `/ai on|off`, `/leave`, `/forget`, `/forget confirm`.

Reminder/challenge controls are `/remind HH:mm|off`, `/quiet HH:mm HH:mm`, `/pause`, `/resume`, `/challenge HABIT_ID|off`, and `/leaderboard`. Participation and sharing remain optional.

Daily totals are absolute: repeating `/done 3 10` does not add another ten. Dates use the membership's timezone. Out-of-order message IDs from the same participant cannot overwrite a newer command. Transcribed voice text is never interpreted as an executable command: AI coaching asks for an explicit `/done` confirmation.

## Processing and delivery guarantees

The webhook authenticates the `X-Telegram-Bot-Api-Secret-Token` header. A database ingress lock serializes short state transactions across replicas. The processed update ID, domain changes and outgoing reply commit atomically. HTTP calls never occur under that database lock.

Addressed natural text/voice goes into a durable queue only for active, consenting members. Worker claims last 120 seconds and provider work has a 90-second deadline. Claims snapshot only the caller's own goals/interests. Consent is checked before download processing, again before transcription, and before publishing the reply. Expired provider claims are marked failed without automatically repeating a potentially billable call; the user can ask again. Terminal queue payloads are scrubbed.

Voice input is bounded to 60 seconds / 5 MiB and downloaded only from fixed Telegram endpoints with bounded response buffers and deadlines. OGG bytes are held in memory and cleared after transcription. AI replies are bounded to 850 characters. Preferred spoken replies contain the entire written reply in the voice caption and identify the voice as AI-generated. Longer command responses are delivered in full as text; synthesis or voice-delivery failures also fall back to text. Revoked/deleted replies are cancelled, not converted into stale text.

Outgoing messages use a durable leased outbox, a conservative three-second global send gate, bounded retries and safe error codes. AI identity/consent and current outbox lease are checked again after synthesis and immediately before sending. Quiet-hour and reminder validity are rechecked by the engagement delivery guard. Delivery is **at least once**: a crash after Telegram accepts a message but before acknowledgement can duplicate a reply. Domain progress remains replay-safe. A message already in flight to Telegram cannot be recalled by a later opt-out.

## Usage controls

Defaults: 20 accepted text-coaching jobs per membership/local day; 5 accepted voice jobs; 20 speech syntheses per Telegram user/UTC day across groups; 200 reserved AI operations globally per UTC day. Text jobs reserve one global operation, voice jobs reserve two (transcription and reply), synthesis reserves another. Failed/cancelled accepted work is not refunded. Values are configurable with `telegram.daily-text-limit`, `telegram.daily-voice-limit`, `telegram.daily-speech-limit`, and `telegram.daily-global-limit`.

Conversation requests have a ten-second member cooldown, at most three outstanding jobs per membership and 1,000 globally. Replies are limited to 20 per member/chat/minute and 10,000 outstanding messages globally. Safety commands still revoke/delete state even when responses are throttled. These are operation caps, not monetary spending guarantees; configure the AI project's own budget/alerts before production.

## Verification and limits

Offline tests cover webhook authentication, malformed/oversized updates, concurrent deduplication, cross-group/user ownership, private summaries, opt-out/deletion, quotas, transcript-only confirmation, consent revocation during voice download, speech fallback and cancellation after synthesis. Provider/Telegram transports are replaced by fakes; no credentials are required for tests. Real Telegram delivery and real provider audio remain production acceptance checks after secret configuration.
