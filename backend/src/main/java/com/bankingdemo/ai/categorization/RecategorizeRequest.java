package com.bankingdemo.ai.categorization;

import jakarta.validation.constraints.NotNull;

public record RecategorizeRequest(
        @NotNull Long categoryId,
        AcceptedSuggestionSource acceptedFrom
) {
    public enum AcceptedSuggestionSource {
        MANUAL, RULE_BASED, AI
    }
}
