package com.bankingdemo.ai.assistant;

import java.util.List;

/**
 * {@code aiGenerated=false} means the narrative was produced entirely by
 * deterministic backend calculation; the frontend must label it distinctly
 * ("Calculated answer") and may show {@code fallbackReason}.
 *
 * {@code verifiedFigures} and {@code relatedTransactions} are always read
 * straight from backend-computed fields, never parsed out of model text, so
 * the amounts and dates a customer relies on are trustworthy even when the
 * narrative came from a model.
 */
public record AssistantAskResponse(
        String answer,
        List<String> relatedTransactionReferences,
        boolean aiGenerated,
        FallbackReason fallbackReason,
        VerifiedFigures verifiedFigures,
        List<VerifiedTransaction> relatedTransactions
) {

    public enum FallbackReason {
        /** The answer is model-written (and passed verification). */
        NONE,
        /** AI is not configured, cooling down, or over its local quota. */
        AI_NOT_AVAILABLE,
        /** The model answered but failed number/date/reference verification, so it was discarded. */
        AI_ANSWER_REJECTED,
        /** The provider call or its reply failed. */
        AI_ERROR
    }

    public record VerifiedFigures(String asOfDate, String spentThisMonth, String spentLastMonth, String currency) {
    }

    public record VerifiedTransaction(String reference, String amount, String date, String description, String category) {
    }

    /** Convenience for the deterministic path before facts are attached. */
    static AssistantAskResponse deterministic(String answer, List<String> references) {
        return new AssistantAskResponse(answer, references, false, FallbackReason.AI_NOT_AVAILABLE, null, List.of());
    }
}
