package com.bankingdemo.ai;

/**
 * Thrown by an {@link AiChatClient} for any reason the caller should treat as
 * "no AI answer this time" -- missing config, timeout, HTTP error, rate limit,
 * or a malformed response. Every caller must catch this and fall back to a
 * non-AI path rather than surfacing it to the customer.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
