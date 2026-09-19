package com.bankingdemo.ai.assistant;

import com.bankingdemo.ai.AiChatClient;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.security.RateLimiter;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/customer/ai")
public class AssistantController {

    private final AssistantService assistantService;
    private final AiChatClient aiChatClient;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;

    public AssistantController(AssistantService assistantService, AiChatClient aiChatClient,
                                RateLimiter rateLimiter, AppProperties appProperties) {
        this.assistantService = assistantService;
        this.aiChatClient = aiChatClient;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
    }

    @PostMapping("/assistant")
    public AssistantAskResponse ask(@Valid @RequestBody AssistantAskRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        if (!rateLimiter.tryConsume("ai:" + customerId, appProperties.getRateLimit().getAiPerMinute(), Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("You're asking too quickly. Please wait a moment and try again.");
        }
        return assistantService.ask(customerId, request.question());
    }

    /** Lets the frontend show "AI unavailable" state without waiting for a failed ask. */
    @GetMapping("/status")
    public AiStatusResponse status() {
        SecurityUtils.requireCustomerId();
        return new AiStatusResponse(aiChatClient.isAvailable());
    }

    public record AiStatusResponse(boolean aiAvailable) {
    }
}
