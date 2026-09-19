package com.bankingdemo.ai.assistant;

import java.util.List;

/**
 * {@code aiGenerated=false} means the answer was produced entirely by
 * deterministic backend calculation (the model was unavailable, disabled, or
 * its reply failed validation) -- the frontend must label this distinctly
 * from a real AI answer, never present it as if the model produced it.
 */
public record AssistantAskResponse(
        String answer,
        List<String> relatedTransactionReferences,
        boolean aiGenerated
) {
}
