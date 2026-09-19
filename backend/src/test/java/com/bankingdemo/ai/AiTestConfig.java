package com.bankingdemo.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real GroqChatClient with a scriptable fake for any test that
 * imports this -- no real network call, no Groq API key needed in CI.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AiTestConfig {

    @Bean
    @Primary
    public AiChatClient aiChatClient() {
        return new FakeAiChatClient();
    }
}
