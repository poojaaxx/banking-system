package com.bankingdemo.ai;

/**
 * Thrown by an {@link AiChatClient} for any reason the caller should treat as
 * "no AI answer this time" -- missing config, local quota, timeout, HTTP
 * error, provider rate limit, or a malformed response. Every caller must
 * catch this and fall back to a non-AI path rather than surfacing it to the
 * customer. Messages must never contain prompt/response content or secrets:
 * they are logged.
 */
public class AiUnavailableException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED, LOCAL_QUOTA, COOLDOWN, PROVIDER_RATE_LIMITED, PROVIDER_AUTH, PROVIDER_ERROR, TIMEOUT_OR_NETWORK, BAD_RESPONSE
    }

    private final Reason reason;
    private final long retryAfterSeconds;

    public AiUnavailableException(Reason reason, String message) {
        this(reason, message, -1, null);
    }

    public AiUnavailableException(Reason reason, String message, long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Reason getReason() {
        return reason;
    }

    /** Provider-suggested wait in seconds, or -1 when the provider did not say. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
