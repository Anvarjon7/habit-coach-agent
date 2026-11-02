package com.example.habit_coach_agent.coaching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("coach.ai")
public class CoachingProperties {
    private boolean enabled;
    private String apiKey;
    private String textModel;
    private String speechModel;
    private String transcriptionModel;
    private String voice;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxInputChars = 4096;
    private int maxContextChars = 8192;
    private int maxOutputChars = 2000;
    private int maxOutputTokens = 800;
    private int maxAudioBytes = 5 * 1024 * 1024;
    private int maxResponseBytes = 8 * 1024 * 1024;
    private int maxConcurrentRequests = 2;

    void validate() {
        if (timeout == null || timeout.toMillis() < 100 || timeout.compareTo(Duration.ofSeconds(120)) > 0
                || maxInputChars < 1 || maxInputChars > 16000 || maxContextChars < 1 || maxContextChars > 32000
                || maxOutputChars < 1 || maxOutputChars > 4000 || maxOutputTokens < 16 || maxOutputTokens > 4096
                || maxAudioBytes < 32 || maxAudioBytes > 10 * 1024 * 1024
                || maxResponseBytes < 32 || maxResponseBytes > 16 * 1024 * 1024
                || maxConcurrentRequests < 1 || maxConcurrentRequests > 8) {
            throw new IllegalArgumentException("Invalid coach.ai limits.");
        }
        if (enabled && (apiKey == null || apiKey.isBlank() || apiKey.length() > 512
                || apiKey.chars().anyMatch(c -> c < 33 || c > 126)
                || !identifier(textModel) || !identifier(speechModel)
                || !identifier(transcriptionModel) || !identifier(voice))) {
            throw new IllegalArgumentException("Enabled coach.ai requires an API key, three model IDs and a voice.");
        }
    }

    private boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getTextModel() { return textModel; }
    public void setTextModel(String value) { textModel = value; }
    public String getSpeechModel() { return speechModel; }
    public void setSpeechModel(String value) { speechModel = value; }
    public String getTranscriptionModel() { return transcriptionModel; }
    public void setTranscriptionModel(String value) { transcriptionModel = value; }
    public String getVoice() { return voice; }
    public void setVoice(String value) { voice = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public int getMaxInputChars() { return maxInputChars; }
    public void setMaxInputChars(int value) { maxInputChars = value; }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int value) { maxContextChars = value; }
    public int getMaxOutputChars() { return maxOutputChars; }
    public void setMaxOutputChars(int value) { maxOutputChars = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    public int getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(int value) { maxAudioBytes = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int value) { maxConcurrentRequests = value; }
}
