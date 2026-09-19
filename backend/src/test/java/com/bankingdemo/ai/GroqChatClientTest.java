package com.bankingdemo.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provider-failure behaviour of the Groq client against a local fake HTTP
 * server: no network, no API key, deterministic. Covers 429 with Retry-After,
 * auth failure, server errors, timeouts, malformed replies, the local quota
 * guard, the not-configured case, and that logs never contain secrets or
 * prompt/response content.
 */
class GroqChatClientTest {

    private static final String API_KEY = "gsk_TEST_KEY_MUST_NEVER_BE_LOGGED";
    private static final String SECRET_PROMPT = "PROMPT_CONTENT_MUST_NEVER_BE_LOGGED_9f3a";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String responseBody = okBody("{\\\"ok\\\":true}");
    private volatile String retryAfter;
    private volatile long delayMillis;

    private AiProperties properties;
    private GroqChatClient client;
    private ListAppender<ILoggingEvent> logs;
    private Logger clientLogger;

    private static String okBody(String escapedContent) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escapedContent + "\",\"reasoning\":\"ignored\"}}],\"usage\":{}}";
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            hits.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (retryAfter != null) {
                exchange.getResponseHeaders().add("retry-after", retryAfter);
            }
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            } finally {
                exchange.close();
            }
        });
        server.start();

        properties = new AiProperties();
        properties.setGroqApiKey(API_KEY);
        properties.setGroqEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions");
        properties.setTimeoutMs(800);
        properties.setFailureCooldownSeconds(60);
        client = newClient(properties);

        clientLogger = (Logger) LoggerFactory.getLogger(GroqChatClient.class);
        logs = new ListAppender<>();
        logs.start();
        clientLogger.addAppender(logs);
        clientLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void stop() {
        clientLogger.detachAppender(logs);
        server.stop(0);
    }

    private static GroqChatClient newClient(AiProperties p) {
        return new GroqChatClient(p, new AiAvailabilityTracker(), new AiQuotaGuard(p, Clock.systemUTC()));
    }

    @Test
    void successReturnsContentAndSendsBoundedRequestWithBearerAuth() {
        String content = client.chat("system", "user", true);

        assertThat(content).isEqualTo("{\"ok\":true}");
        assertThat(authHeaders.get(0)).isEqualTo("Bearer " + API_KEY);
        String body = requestBodies.get(0);
        assertThat(body).contains("\"model\":\"openai/gpt-oss-20b\"");
        assertThat(body).contains("\"max_completion_tokens\":600");
        assertThat(body).contains("\"response_format\"");
        assertThat(body).contains("\"reasoning_effort\":\"low\"");
    }

    @Test
    void reasoningEffortIsOnlySentToGptOssModels() {
        properties.setGroqModel("qwen/qwen3.8-27b");
        client = newClient(properties);
        client.chat("system", "user", false);
        assertThat(requestBodies.get(0)).doesNotContain("reasoning_effort").doesNotContain("response_format");
    }

    @Test
    void providerRateLimitHonorsRetryAfterAndThenStopsCallingDuringCooldown() {
        status = 429;
        retryAfter = "7";

        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.PROVIDER_RATE_LIMITED);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(7);
                });
        assertThat(client.isAvailable()).isFalse();

        int hitsAfterFirst = hits.get();
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.COOLDOWN));
        assertThat(hits.get()).as("no request may reach the provider while cooling down").isEqualTo(hitsAfterFirst);
    }

    @Test
    void authenticationFailureIsReportedAsSuchAndTripsACooldown() {
        status = 401;
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.PROVIDER_AUTH));
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void serverErrorFallsBackWithoutLeakingBody() {
        status = 500;
        responseBody = "{\"error\":\"boom SECRET_SERVER_BODY\"}";
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.PROVIDER_ERROR);
                    assertThat(e.getMessage()).doesNotContain("SECRET_SERVER_BODY");
                });
    }

    @Test
    void aSlowProviderIsCutOffByTheTimeout() {
        delayMillis = 2500;
        long started = System.nanoTime();
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.TIMEOUT_OR_NETWORK));
        assertThat((System.nanoTime() - started) / 1_000_000).as("timeout must bound the wait").isLessThan(2400);
    }

    @Test
    void emptyChoicesAndMalformedBodiesAreUnavailableNotCrashes() {
        responseBody = "{\"choices\":[]}";
        assertThatThrownBy(() -> client.chat("s", "u", true)).isInstanceOf(AiUnavailableException.class);

        client = newClient(properties);
        responseBody = "this is not json";
        assertThatThrownBy(() -> client.chat("s", "u", true)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void localQuotaStopsCallsBeforeTheyReachTheProvider() {
        properties.setMaxCallsPerMinute(2);
        client = newClient(properties);

        client.chat("s", "u", true);
        client.chat("s", "u", true);
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.LOCAL_QUOTA));
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void anOversizedPromptIsRefusedByTheTokenBudgetWithoutACall() {
        properties.setMaxEstimatedTokensPerMinute(1000);
        client = newClient(properties);
        assertThatThrownBy(() -> client.chat("s", "x".repeat(20_000), true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.LOCAL_QUOTA));
        assertThat(hits.get()).isZero();
    }

    @Test
    void whenNotConfiguredNothingIsSentAndItIsReportedUnavailable() {
        properties.setGroqApiKey("");
        client = newClient(properties);
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.chat("s", "u", true))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiUnavailableException.Reason.NOT_CONFIGURED));
        assertThat(hits.get()).isZero();
    }

    @Test
    void logsNeverContainTheApiKeyPromptOrProviderBody() {
        for (int code : new int[]{429, 401, 500}) {
            status = code;
            responseBody = "{\"error\":\"PROVIDER_BODY_MUST_NEVER_BE_LOGGED\"}";
            retryAfter = "5";
            client = newClient(properties);
            assertThatThrownBy(() -> client.chat("system", SECRET_PROMPT, true)).isInstanceOf(AiUnavailableException.class);
        }
        delayMillis = 2000;
        status = 200;
        client = newClient(properties);
        assertThatThrownBy(() -> client.chat("system", SECRET_PROMPT, true)).isInstanceOf(AiUnavailableException.class);

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            String rendered = event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage());
            assertThat(rendered).doesNotContain(API_KEY).doesNotContain(SECRET_PROMPT).doesNotContain("PROVIDER_BODY_MUST_NEVER_BE_LOGGED");
            assertThat(event.getThrowableProxy()).as("no stack traces with payloads").isNull();
        }
    }
}
