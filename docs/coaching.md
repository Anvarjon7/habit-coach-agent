# Text and audio coaching provider

This slice supplies a read-only Java adapter. `CoachProvider.reply(message, context)` produces text, `speak(text)` produces OGG/Opus bytes, and `transcribe(ogg)` returns text from a Telegram voice message. It does not change habits, progress, identity or preferences. The Telegram/application layer must enforce consent and group isolation, provide only necessary participant context, and treat transcripts as untrusted participant input.

## Configuration

The default `CatalogCoachProvider` supplies a small, explicit basic-text mode without external calls. Speech and transcription raise `CoachProviderException` with reason `UNAVAILABLE`; callers should explain this and offer written interaction. It does not pretend that recorded audio or personalized model reasoning exists.

Enable the external adapter using Spring properties below or their relaxed-binding environment variables. No models are silently selected. Supply model identifiers that the account can access and that support the corresponding documented endpoint.

| Spring property | Environment variable | Default |
| --- | --- | --- |
| `coach.ai.enabled` | `COACH_AI_ENABLED` | `false` |
| `coach.ai.api-key` | `COACH_AI_APIKEY` | required when enabled |
| `coach.ai.text-model` | `COACH_AI_TEXTMODEL` | required when enabled |
| `coach.ai.speech-model` | `COACH_AI_SPEECHMODEL` | required when enabled |
| `coach.ai.transcription-model` | `COACH_AI_TRANSCRIPTIONMODEL` | required when enabled |
| `coach.ai.voice` | `COACH_AI_VOICE` | required when enabled |
| `coach.ai.timeout` | `COACH_AI_TIMEOUT` | `30s`, range 100ms–120s |
| `coach.ai.max-input-chars` | `COACH_AI_MAXINPUTCHARS` | 4096 |
| `coach.ai.max-context-chars` | `COACH_AI_MAXCONTEXTCHARS` | 8192 |
| `coach.ai.max-output-chars` | `COACH_AI_MAXOUTPUTCHARS` | 2000 |
| `coach.ai.max-output-tokens` | `COACH_AI_MAXOUTPUTTOKENS` | 800 |
| `coach.ai.max-audio-bytes` | `COACH_AI_MAXAUDIOBYTES` | 5242880 (5 MiB) |
| `coach.ai.max-response-bytes` | `COACH_AI_MAXRESPONSEBYTES` | 8388608 (8 MiB) |
| `coach.ai.max-concurrent-requests` | `COACH_AI_MAXCONCURRENTREQUESTS` | 2 per application process |

Spring removes dashes inside each property segment when mapping environment variables, hence `APIKEY` rather than `API_KEY`. Keep the key in the hosting secret store; never in committed properties or logs. Invalid enabled configuration fails startup with a generic error that excludes secret values.

## API and failure behavior

Requests use only fixed `https://api.openai.com/v1/` endpoints; there is no configurable host or participant-controlled URL. Redirects are disabled. The adapter uses `POST /responses` with `store=false`, no tools, a bounded output-token budget, and a separate system instruction; participant message and context stay in user input. It walks assistant message content for `output_text` instead of assuming the first output item contains text. Incomplete or malformed responses fail explicitly.

Speech uses `POST /audio/speech` with `response_format=opus`. The return value is checked for OGG/Opus identification before delivery. The caller must clearly identify the voice as AI-generated and include the exact source text as the transcript; do not transcribe synthesized output again. Incoming audio uses multipart `POST /audio/transcriptions`, an `audio/ogg` file named `voice.ogg`, the configured model and JSON response format. Only a nonempty, bounded text transcript is returned. OGG signature checks provide basic format screening, not full codec validation; real-media acceptance is still required.

The request timeout covers waiting for the entire bounded response. A custom response subscriber cancels before buffering data over the configured limit, including chunked bodies. Text/transcription response bodies have a stricter 128 KiB maximum even when audio responses may be larger. Input sizes and concurrency are bounded. No automatic retries occur, avoiding accidental duplicate spend; application-level durable retry behavior needs its own policy. HTTP 429 and exhausted concurrency return `BUSY`; timeouts return `TIMEOUT`. Other failures return generic `PROVIDER_ERROR` without raw response bodies, cause chains, user content or keys. The caller can offer basic text-mode coaching after a failure and must not silently claim audio succeeded.

## Coaching content

The system instructions identify an AI coach, avoid age-based access restrictions and stereotypes, and encourage manageable participant-selected actions. They prohibit claims that the model recorded progress or changed settings, fabricated citations, fixed habit-formation deadlines, pressure and shame. The reference catalog is intentionally small: [Singh et al. (2024), Time to Form a Habit](https://pubmed.ncbi.nlm.nih.gov/39685110/). This review concerns adult health behaviours, has substantial variability and study-quality limitations, and does not prove that the same outcomes apply to children or learning habits. Examples for reading and language practice are practical product suggestions rather than proven interventions for all ages.

Prompt instructions are a mitigation rather than proof of model reliability. The model has no database tools in this slice, so generated content cannot directly grant access or mutate progress. A production evaluation set should still check misleading claims, private-data requests, harmful challenges, and appropriate responses across participant preferences and languages.

## Verification and production limits

Offline tests inject a fake HTTP transport: protocol payloads and fixed endpoints, untrusted input placement, Responses state storage disabled and no model tools, speech/transcription formats, malformed and incomplete responses, content-free errors, redirect/rate-limit behavior, request/output bounds, response cancellation, concurrency limits and explicit disabled behavior. They use no API key, provider account, paid requests or external network.

`store=false` disables Responses application-state storage. It does not guarantee that the provider retains no data: provider processing, abuse-monitoring retention, and account-specific data controls must be disclosed and reviewed before enabling live coaching. Consent must cover messages, relevant goals/interests, and incoming voice audio being sent to the configured provider.

This is not yet a live provider acceptance result. Before production, choose and authorize provider costs, configure account-specific model IDs and a key, test authentic Telegram OGG voice messages and playback, verify supported languages/voices and model reliability, and configure per-user/group quotas and provider project spend controls. Concurrency limits alone are not a billing cap. Never send another group's data or unnecessary personal profile fields as context.

The [official text-to-speech guide](https://developers.openai.com/api/docs/guides/text-to-speech), fetched 2 November 2026, currently flags the existing Speech API models for shutdown on 6 January 2027 and describes migration to a different Realtime API workflow. Recheck availability before production; changing a model name alone does not migrate workflows. This adapter deliberately avoids a hardcoded model default and does not claim to implement Realtime.

Implementation sources: [OpenAI text generation and Responses](https://developers.openai.com/api/docs/guides/text), [speech generation](https://developers.openai.com/api/docs/guides/text-to-speech), [transcription request/response reference](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create). The Telegram transport must deliver Opus using the platform's supported voice-upload workflow.
