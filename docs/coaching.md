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
| `coach.ai.ffmpeg-executable` | `COACH_AI_FFMPEGEXECUTABLE` | `ffmpeg`; executable path, never a shell command |
| `coach.ai.timeout` | `COACH_AI_TIMEOUT` | `30s`, range 100ms–120s |
| `coach.ai.max-input-chars` | `COACH_AI_MAXINPUTCHARS` | 4096 |
| `coach.ai.max-context-chars` | `COACH_AI_MAXCONTEXTCHARS` | 8192 |
| `coach.ai.max-output-chars` | `COACH_AI_MAXOUTPUTCHARS` | 2000 |
| `coach.ai.max-output-tokens` | `COACH_AI_MAXOUTPUTTOKENS` | 800 |
| `coach.ai.max-speech-output-tokens` | `COACH_AI_MAXSPEECHOUTPUTTOKENS` | 4096; speech has its own token budget |
| `coach.ai.max-audio-bytes` | `COACH_AI_MAXAUDIOBYTES` | 5242880 (5 MiB) |
| `coach.ai.max-response-bytes` | `COACH_AI_MAXRESPONSEBYTES` | 8388608 (8 MiB) |
| `coach.ai.max-concurrent-requests` | `COACH_AI_MAXCONCURRENTREQUESTS` | 2 per application process |

Spring removes dashes inside each property segment when mapping environment variables, hence `APIKEY` rather than `API_KEY`. Keep the key in the hosting secret store; never in committed properties or logs. Invalid enabled configuration fails startup with a generic error that excludes secret values.

## API and failure behavior

Requests use only fixed OpenAI HTTPS and Realtime WebSocket endpoints; there is no configurable host or participant-controlled URL. Redirects are disabled. Text uses `POST /responses` with `store=false`, no tools, a bounded output-token budget, and a separate system instruction; participant message and context stay in user input. It walks assistant message content for `output_text` instead of assuming the first output item contains text. Incomplete or malformed responses fail explicitly.

Speech uses a short-lived Realtime WebSocket session with an explicitly configured supported speech model. It requests 24 kHz PCM and compares the returned transcript with the source reply before encoding it to OGG/Opus through FFmpeg. Transcript mismatch, excessive output, timeout or encoder failure causes the existing written fallback. The caller clearly identifies the voice as AI-generated. Transcript matching is a consistency check, not proof that the audio is accurately pronounced; authentic playback remains an acceptance requirement.

FFmpeg is included in the runtime image. For local development, install FFmpeg with the `libopus` encoder and put it on PATH, or set `COACH_AI_FFMPEGEXECUTABLE` to its executable path. The encoder receives audio through standard input/output without a shell or temporary media files. It never receives the API key. The encoding step shares the speech operation's deadline.

Incoming audio continues to use multipart `POST /audio/transcriptions`, an `audio/ogg` file named `voice.ogg`, the configured model and JSON response format. Only a nonempty, bounded text transcript is returned. OGG signature checks provide basic format screening, not full codec validation; real-media acceptance is still required.

The request timeout covers waiting for the entire bounded response. A custom response subscriber cancels before buffering data over the configured limit, including chunked bodies. Text/transcription response bodies have a stricter 128 KiB maximum even when audio responses may be larger. Input sizes and concurrency are bounded. No automatic retries occur, avoiding accidental duplicate spend; application-level durable retry behavior needs its own policy. HTTP 429 and exhausted concurrency return `BUSY`; timeouts return `TIMEOUT`. Other failures return generic `PROVIDER_ERROR` without raw response bodies, cause chains, user content or keys. The caller can offer basic text-mode coaching after a failure and must not silently claim audio succeeded.

## Coaching content

The system instructions identify an AI coach, avoid age-based access restrictions and stereotypes, and encourage manageable participant-selected actions. They prohibit claims that the model recorded progress or changed settings, fabricated citations, fixed habit-formation deadlines, pressure and shame. The reference catalog is intentionally small: [Singh et al. (2024), Time to Form a Habit](https://pubmed.ncbi.nlm.nih.gov/39685110/). This review concerns adult health behaviours, has substantial variability and study-quality limitations, and does not prove that the same outcomes apply to children or learning habits. Examples for reading and language practice are practical product suggestions rather than proven interventions for all ages.

Prompt instructions are a mitigation rather than proof of model reliability. The model has no database tools in this slice, so generated content cannot directly grant access or mutate progress. A production evaluation set should still check misleading claims, private-data requests, harmful challenges, and appropriate responses across participant preferences and languages.

## Verification and production limits

Offline tests inject a fake HTTP transport: protocol payloads and fixed endpoints, untrusted input placement, Responses state storage disabled and no model tools, speech/transcription formats, malformed and incomplete responses, content-free errors, redirect/rate-limit behavior, request/output bounds, response cancellation, concurrency limits and explicit disabled behavior. They use no API key, provider account, paid requests or external network.

`store=false` disables Responses application-state storage. It does not guarantee that the provider retains no data: provider processing, abuse-monitoring retention, and account-specific data controls must be disclosed and reviewed before enabling live coaching. Consent must cover messages, relevant goals/interests, and incoming voice audio being sent to the configured provider.

This is not yet a live provider acceptance result. Before production, choose and authorize provider costs, configure account-specific model IDs and a key, test authentic Telegram OGG voice messages and playback, verify supported languages/voices and model reliability, and configure per-user/group quotas and provider project spend controls. Concurrency limits alone are not a billing cap. Never send another group's data or unnecessary personal profile fields as context.

The deprecated `/audio/speech` workflow has been replaced. Configure a model that supports Realtime audio output; an old TTS model name will not work on the new endpoint. Recheck account access, supported voice/language behavior and prices before live acceptance. No model is silently selected and no paid request is made by automated tests.

Implementation sources: [OpenAI text generation and Responses](https://developers.openai.com/api/docs/guides/text), [speech generation](https://developers.openai.com/api/docs/guides/text-to-speech), [transcription request/response reference](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create). The Telegram transport must deliver Opus using the platform's supported voice-upload workflow.

Realtime protocol sources: [WebSocket connections](https://developers.openai.com/api/docs/guides/realtime-websocket), [conversation and audio events](https://developers.openai.com/api/docs/guides/realtime-conversations), [client events](https://developers.openai.com/api/reference/resources/realtime/client-events).
