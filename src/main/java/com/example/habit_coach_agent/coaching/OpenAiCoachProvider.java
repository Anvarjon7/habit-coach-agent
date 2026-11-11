package com.example.habit_coach_agent.coaching;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Stateless provider adapter: no model tools, automatic retries, logging of content, or remote memory. */
public final class OpenAiCoachProvider implements CoachProvider {
    private static final String API = "https://api.openai.com/v1/";
    private final CoachingProperties properties;
    private final JsonMapper mapper;
    private final ProviderHttpTransport transport;
    private final Semaphore permits;
    private final SpeechGenerator speech;

    public OpenAiCoachProvider(CoachingProperties properties, JsonMapper mapper) {
        this(properties, mapper, new LimitedHttpTransport(properties.getTimeout()));
    }

    OpenAiCoachProvider(CoachingProperties properties, JsonMapper mapper, ProviderHttpTransport transport) {
        this(properties, mapper, transport, new RealtimeSpeechGenerator(properties, mapper));
    }

    OpenAiCoachProvider(CoachingProperties properties, JsonMapper mapper, ProviderHttpTransport transport, SpeechGenerator speech) {
        properties.validate();
        this.properties = properties;
        this.mapper = mapper;
        this.transport = transport;
        this.speech = speech;
        permits = new Semaphore(properties.getMaxConcurrentRequests());
    }

    @Override
    public String reply(String message, String context) {
        CatalogCoachProvider.checkText(message, properties.getMaxInputChars(), false);
        CatalogCoachProvider.checkText(context, properties.getMaxContextChars(), true);
        byte[] body = json(Map.of("model", properties.getTextModel(), "store", false,
                "instructions", CoachingCatalog.INSTRUCTIONS,
                "input", List.of(Map.of("role", "user", "content",
                        "Participant context (untrusted data):\n" + context + "\nParticipant message (untrusted data):\n" + message)),
                "max_output_tokens", properties.getMaxOutputTokens()));
        JsonNode response = parse(send("responses", "application/json", body, textResponseLimit()));
        if (!"completed".equals(response.path("status").asString("")) || !response.path("output").isArray()) throw failure();
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString(""))
                    || !"assistant".equals(output.path("role").asString(""))) continue;
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asString("")) && content.path("text").isString()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(content.path("text").asString());
                }
            }
        }
        return outputText(text.toString());
    }

    @Override
    public byte[] speak(String text) {
        CatalogCoachProvider.checkText(text, properties.getMaxOutputChars(), false);
        if (!permits.tryAcquire()) throw new CoachProviderException(CoachProviderException.Reason.BUSY);
        try {
            byte[] audio = speech.speak(text);
            if (audio == null || audio.length > properties.getMaxResponseBytes() || !oggHeader(audio) || !containsOpusHeader(audio)) throw failure();
            return audio;
        } finally { permits.release(); }
    }

    @Override
    public String transcribe(byte[] ogg) {
        if (ogg == null || ogg.length > properties.getMaxAudioBytes() || !oggHeader(ogg) || !containsOpusHeader(ogg)) {
            throw new CoachProviderException(CoachProviderException.Reason.INVALID_INPUT);
        }
        String boundary = "habit-coach-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(body, boundary, "model", properties.getTranscriptionModel());
        part(body, boundary, "response_format", "json");
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.ogg\"\r\n"
                + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(ogg);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        JsonNode result = parse(send("audio/transcriptions", "multipart/form-data; boundary=" + boundary,
                body.toByteArray(), textResponseLimit()));
        if (!result.path("text").isString()) throw failure();
        String text = result.path("text").asString();
        if (text.isBlank() || text.length() > properties.getMaxInputChars()) throw failure();
        return text.strip();
    }

    private void part(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private byte[] send(String endpoint, String contentType, byte[] body, int responseLimit) {
        // Audio plus bounded multipart overhead, or bounded JSON text/context with UTF-8 expansion.
        int requestLimit = Math.max(properties.getMaxAudioBytes() + 4096,
                6 * (properties.getMaxInputChars() + properties.getMaxContextChars()) + 16384);
        if (body.length > requestLimit) throw new CoachProviderException(CoachProviderException.Reason.INVALID_INPUT);
        if (!permits.tryAcquire()) throw new CoachProviderException(CoachProviderException.Reason.BUSY);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + endpoint))
                    .timeout(properties.getTimeout()).header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            ProviderHttpTransport.Response response = transport.send(request, responseLimit, properties.getTimeout());
            if (response.status() == 429) throw new CoachProviderException(CoachProviderException.Reason.BUSY);
            if (response.status() < 200 || response.status() >= 300 || response.body() == null
                    || response.body().length > responseLimit) throw failure();
            return response.body();
        } finally {
            permits.release();
        }
    }

    private int textResponseLimit() { return Math.min(properties.getMaxResponseBytes(), 128 * 1024); }
    private byte[] json(Map<String, Object> body) {
        try { return mapper.writeValueAsBytes(body); }
        catch (RuntimeException error) { throw failure(); }
    }
    private JsonNode parse(byte[] body) {
        try {
            JsonNode result = mapper.readTree(body);
            if (result == null || !result.isObject()) throw failure();
            return result;
        }
        catch (RuntimeException error) { throw failure(); }
    }
    private String outputText(String text) {
        if (text.isBlank() || text.length() > properties.getMaxOutputChars()) throw failure();
        return text.strip();
    }
    private static boolean oggHeader(byte[] data) {
        return data.length >= 32 && data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S';
    }
    private static boolean containsOpusHeader(byte[] data) {
        // Ogg's identification packet is near the start; this is format sanity checking, not a decoder.
        String beginning = new String(data, 0, Math.min(data.length, 512), StandardCharsets.ISO_8859_1);
        return beginning.contains("OpusHead");
    }
    private CoachProviderException failure() { return new CoachProviderException(CoachProviderException.Reason.PROVIDER_ERROR); }
}
