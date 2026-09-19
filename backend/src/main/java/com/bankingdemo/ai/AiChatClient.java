package com.bankingdemo.ai;

/**
 * Abstraction over "call a language model and get text back", so tests can
 * inject a fake implementation instead of making real network calls (Groq
 * free-tier keys are per-developer and must never be required for the test
 * suite to pass). The real implementation is {@link GroqChatClient}.
 */
public interface AiChatClient {

    /**
     * @param systemPrompt fixed instructions written by us -- never influenced by customer input.
     * @param userContent  the untrusted part: customer question and/or data pulled from customer records.
     * @param jsonMode     if true, ask the model to return a single JSON object.
     * @return raw text content of the model's reply.
     * @throws AiUnavailableException if the model could not be reached or refused the request;
     *                                callers must treat this as routine and fall back.
     */
    String chat(String systemPrompt, String userContent, boolean jsonMode) throws AiUnavailableException;

    boolean isAvailable();
}
