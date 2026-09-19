package com.bankingdemo.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Talks to Groq's OpenAI-compatible chat completions endpoint
 * (https://console.groq.com/docs/api-reference#chat-create). Groq's free
 * developer tier requires no credit card and, per its Services Agreement,
 * does not train on customer inputs/outputs -- see docs/ai-features.md.
 *
 * This client has no function-calling / tool-use wiring: the model can only
 * return text. It has no access to move money, change balances, freeze
 * accounts, or run SQL -- see AssistantService and TransactionCategorizationService
 * for how (and how little) of the model's output is trusted.
 */
@Component
public class GroqChatClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final AiProperties properties;
    private final AiAvailabilityTracker availabilityTracker;
    private final RestClient restClient;

    public GroqChatClient(AiProperties properties, AiAvailabilityTracker availabilityTracker) {
        this.properties = properties;
        this.availabilityTracker = availabilityTracker;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(ENDPOINT)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured() && !availabilityTracker.isInCooldown();
    }

    @Override
    public String chat(String systemPrompt, String userContent, boolean jsonMode) {
        if (!isAvailable()) {
            throw new AiUnavailableException("AI is not configured or is in cooldown after a recent failure");
        }

        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "model", properties.getGroqModel(),
                "temperature", 0.2,
                "max_completion_tokens", 700,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent))));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            GroqChatResponse response = restClient.post()
                    .header("Authorization", "Bearer " + properties.getGroqApiKey())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new AiUnavailableException("Groq returned HTTP " + res.getStatusCode().value());
                    })
                    .body(GroqChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiUnavailableException("Groq returned no choices");
            }
            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                throw new AiUnavailableException("Groq returned empty content");
            }
            availabilityTracker.recordSuccess();
            return content;
        } catch (AiUnavailableException e) {
            log.warn("Groq call failed, entering cooldown: {}", e.getMessage());
            availabilityTracker.recordFailure(Duration.ofSeconds(properties.getFailureCooldownSeconds()));
            throw e;
        } catch (RestClientException e) {
            log.warn("Groq call failed (transport), entering cooldown: {}", e.getMessage());
            availabilityTracker.recordFailure(Duration.ofSeconds(properties.getFailureCooldownSeconds()));
            throw new AiUnavailableException("Groq call failed", e);
        }
    }

    private record GroqChatResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    private record Message(String content) {
    }
}
