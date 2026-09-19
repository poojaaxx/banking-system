package com.bankingdemo.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI features are entirely optional. When groqApiKey is blank, {@link #isConfigured()}
 * is false and every AI-backed service must fall back to a non-AI path -- never
 * throw or block a banking operation on this being absent.
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String groqApiKey = "";
    private String groqModel = "llama-3.3-70b-versatile";
    private int timeoutMs = 8000;
    private int failureCooldownSeconds = 120;

    public boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public String getGroqModel() {
        return groqModel;
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel;
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
}
