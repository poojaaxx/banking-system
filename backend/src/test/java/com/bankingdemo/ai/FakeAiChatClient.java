package com.bankingdemo.ai;

/**
 * Test double standing in for a real Groq call. Scripted per-test so we can
 * deterministically exercise: a normal reply, a hallucinated/out-of-bounds
 * reply (the "incorrect answer" case callers must validate against), and a
 * simulated outage -- without ever making a real network call or requiring a
 * Groq API key in CI.
 */
public class FakeAiChatClient implements AiChatClient {

    private boolean available = true;
    private String scriptedResponse;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void respondWith(String rawJsonContent) {
        this.scriptedResponse = rawJsonContent;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String chat(String systemPrompt, String userContent, boolean jsonMode) {
        if (!available) {
            throw new AiUnavailableException("simulated outage");
        }
        if (scriptedResponse == null) {
            throw new AiUnavailableException("no scripted response configured for this test");
        }
        return scriptedResponse;
    }
}
