package com.bankingdemo.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI features are entirely optional. When groqApiKey is blank, {@link #isConfigured()}
 * is false and every AI-backed service must fall back to a non-AI path -- never
 * throw or block a banking operation on this being absent.
 *
 * The local quota limits deliberately sit below Groq's free-plan limits
 * (verified 2026-09-19 for openai/gpt-oss-20b: 30 RPM, 1K RPD, 8K TPM, 200K TPD,
 * enforced per organization) so this app stops calling before the provider
 * starts returning 429s. See docs/ai-features.md.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String groqApiKey = "";
    private String groqEndpoint = "https://api.groq.com/openai/v1/chat/completions";
    private String groqModel = "openai/gpt-oss-20b";
    private String reasoningEffort = "low";
    private int maxCompletionTokens = 600;
    private int timeoutMs = 8000;
    private int failureCooldownSeconds = 120;
    private boolean includeDescriptions = true;

    private int maxCallsPerMinute = 20;
    private int maxCallsPerDay = 800;
    private int maxEstimatedTokensPerMinute = 6000;
    private int maxEstimatedTokensPerDay = 150_000;

    public boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public String getGroqEndpoint() {
        return groqEndpoint;
    }

    public void setGroqEndpoint(String groqEndpoint) {
        this.groqEndpoint = groqEndpoint;
    }

    public String getGroqModel() {
        return groqModel;
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getFailureCooldownSeconds() {
        return failureCooldownSeconds;
    }

    public void setFailureCooldownSeconds(int failureCooldownSeconds) {
        this.failureCooldownSeconds = failureCooldownSeconds;
    }

    public boolean isIncludeDescriptions() {
        return includeDescriptions;
    }

    public void setIncludeDescriptions(boolean includeDescriptions) {
        this.includeDescriptions = includeDescriptions;
    }

    public int getMaxCallsPerMinute() {
        return maxCallsPerMinute;
    }

    public void setMaxCallsPerMinute(int maxCallsPerMinute) {
        this.maxCallsPerMinute = maxCallsPerMinute;
    }

    public int getMaxCallsPerDay() {
        return maxCallsPerDay;
    }

    public void setMaxCallsPerDay(int maxCallsPerDay) {
        this.maxCallsPerDay = maxCallsPerDay;
    }

    public int getMaxEstimatedTokensPerMinute() {
        return maxEstimatedTokensPerMinute;
    }

    public void setMaxEstimatedTokensPerMinute(int maxEstimatedTokensPerMinute) {
        this.maxEstimatedTokensPerMinute = maxEstimatedTokensPerMinute;
    }

    public int getMaxEstimatedTokensPerDay() {
        return maxEstimatedTokensPerDay;
    }

    public void setMaxEstimatedTokensPerDay(int maxEstimatedTokensPerDay) {
        this.maxEstimatedTokensPerDay = maxEstimatedTokensPerDay;
    }
}
