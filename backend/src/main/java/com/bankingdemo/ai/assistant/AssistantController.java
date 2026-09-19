package com.bankingdemo.ai.assistant;

import com.bankingdemo.ai.AiChatClient;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/ai")
public class AssistantController {

    private final AssistantService assistantService;
    private final AiChatClient aiChatClient;

    public AssistantController(AssistantService assistantService, AiChatClient aiChatClient) {
        this.assistantService = assistantService;
        this.aiChatClient = aiChatClient;
    }

    @PostMapping("/assistant")
    public AssistantAskResponse ask(@Valid @RequestBody AssistantAskRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
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
