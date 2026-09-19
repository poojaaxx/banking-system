package com.bankingdemo.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Groq's OpenAI-compatible chat completions endpoint. Groq is
 * optional; every failure mode maps to {@link AiUnavailableException} so
 * callers fall back to the labeled deterministic path.
 *
 * Hardening:
 *  - bounded: request/response timeouts, capped completion tokens, and a local
 *    calls/tokens quota that stays under Groq's free-plan limits
 *  - provider 429 honors Retry-After (bounded); 401/403 trips a long cooldown
 *  - privacy-safe logging: never logs prompts, model output, exception
 *    messages from the HTTP layer, or the API key -- only a reason code and
 *    HTTP status
 *  - no tool/function-calling: the model can only return text
 */
@Component
public class GroqChatClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final long MIN_COOLDOWN_SECONDS = 5;
    private static final long MAX_COOLDOWN_SECONDS = 15 * 60;
    private static final long AUTH_FAILURE_COOLDOWN_SECONDS = 10 * 60;
    private static final int MAX_RESPONSE_CHARS = 4000;

    private final AiProperties properties;
    private final AiAvailabilityTracker availabilityTracker;
    private final AiQuotaGuard quotaGuard;
    private final RestClient restClient;

    public GroqChatClient(AiProperties properties, AiAvailabilityTracker availabilityTracker, AiQuotaGuard quotaGuard) {
        this.properties = properties;
        this.availabilityTracker = availabilityTracker;
        this.quotaGuard = quotaGuard;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.getGroqEndpoint())
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured() && !availabilityTracker.isInCooldown() && quotaGuard.hasHeadroom();
    }

    @Override
    public String chat(String systemPrompt, String userContent, boolean jsonMode) {
        if (!properties.isConfigured()) {
            throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED, "AI is not configured");
        }
        if (availabilityTracker.isInCooldown()) {
            throw new AiUnavailableException(AiUnavailableException.Reason.COOLDOWN, "AI is cooling down after a recent failure");
        }
        int estimatedTokens = AiQuotaGuard.estimateTokens(systemPrompt, userContent, properties.getMaxCompletionTokens());
        if (!quotaGuard.tryAcquire(estimatedTokens)) {
            throw new AiUnavailableException(AiUnavailableException.Reason.LOCAL_QUOTA, "Local AI quota reached; staying under the provider's free-plan limits");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getGroqModel());
        body.put("temperature", 0.2);
        body.put("max_completion_tokens", properties.getMaxCompletionTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (properties.getGroqModel().startsWith("openai/gpt-oss") && !properties.getReasoningEffort().isBlank()) {
            body.put("reasoning_effort", properties.getReasoningEffort());
        }

        try {
            GroqChatResponse response = restClient.post()
                    .header("Authorization", "Bearer " + properties.getGroqApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw fromStatus(res.getStatusCode().value(), res.getHeaders().getFirst("retry-after"));
                    })
                    .body(GroqChatResponse.class);

            String content = extractContent(response);
            availabilityTracker.recordSuccess();
            return content;
        } catch (AiUnavailableException e) {
            tripCooldown(e);
            throw e;
        } catch (RestClientException e) {
            AiUnavailableException wrapped = new AiUnavailableException(AiUnavailableException.Reason.TIMEOUT_OR_NETWORK,
                    "Groq call failed (" + e.getClass().getSimpleName() + ")", -1, null);
            tripCooldown(wrapped);
            throw wrapped;
        }
    }

    private static AiUnavailableException fromStatus(int status, String retryAfterHeader) {
        if (status == 429) {
            return new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_RATE_LIMITED,
                    "Groq rate limit (HTTP 429)", parseSeconds(retryAfterHeader), null);
        }
        if (status == 401 || status == 403) {
            return new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_AUTH,
                    "Groq rejected the credentials (HTTP " + status + ")");
        }
        return new AiUnavailableException(AiUnavailableException.Reason.PROVIDER_ERROR, "Groq returned HTTP " + status);
    }

    private static long parseSeconds(String header) {
        if (header == null) {
            return -1;
        }
        try {
            return Math.max(0, (long) Math.ceil(Double.parseDouble(header.trim())));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void tripCooldown(AiUnavailableException e) {
        long seconds = switch (e.getReason()) {
            case PROVIDER_RATE_LIMITED -> e.getRetryAfterSeconds() > 0 ? e.getRetryAfterSeconds() : properties.getFailureCooldownSeconds();
            case PROVIDER_AUTH -> AUTH_FAILURE_COOLDOWN_SECONDS;
            case LOCAL_QUOTA, NOT_CONFIGURED, COOLDOWN -> 0;
            default -> properties.getFailureCooldownSeconds();
        };
        if (seconds <= 0) {
            log.info("AI call skipped: reason={}", e.getReason());
            return;
        }
        long bounded = Math.min(MAX_COOLDOWN_SECONDS, Math.max(MIN_COOLDOWN_SECONDS, seconds));
        availabilityTracker.recordFailure(Duration.ofSeconds(bounded));
        log.warn("AI provider call failed: reason={} cooldownSeconds={}", e.getReason(), bounded);
    }

    private static String extractContent(GroqChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new AiUnavailableException(AiUnavailableException.Reason.BAD_RESPONSE, "Groq returned no usable choice");
        }
        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank() || content.length() > MAX_RESPONSE_CHARS) {
            throw new AiUnavailableException(AiUnavailableException.Reason.BAD_RESPONSE, "Groq returned empty or oversized content");
        }
        return content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }
}
