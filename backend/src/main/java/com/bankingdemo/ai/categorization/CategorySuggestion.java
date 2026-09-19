package com.bankingdemo.ai.categorization;

/**
 * {@code source} must be surfaced verbatim by the frontend as the suggestion's
 * label ("Rule-based suggestion" vs "AI suggestion") -- never blur the two.
 */
public record CategorySuggestion(
        Long categoryId,
        String categoryCode,
        String categoryName,
        CategorySuggestionSource source,
        Double confidence
) {
}
