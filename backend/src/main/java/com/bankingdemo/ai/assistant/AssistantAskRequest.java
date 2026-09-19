package com.bankingdemo.ai.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantAskRequest(
        @NotBlank @Size(max = 500) String question
) {
}
