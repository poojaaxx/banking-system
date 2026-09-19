package com.bankingdemo.ai.assistant;

import java.util.List;

/**
 * Everything the model is allowed to know for one assistant question, already
 * scoped to a single authenticated customer and pre-computed by backend code
 * (never left for the model to calculate). Deliberately excludes: customer
 * name/email/username, full account numbers, passwords, recovery codes, and
 * session identifiers -- see docs/ai-features.md "data minimization".
 */
public record AssistantContext(
        String todayUtc,
        List<AccountFact> accounts,
        String totalSpendThisMonth,
        String totalSpendLastMonth,
        List<CategoryAmountFact> spendByCategoryThisMonth,
        List<TransactionFact> largestPaymentsLast90Days,
        List<TransactionFact> recentTransactions
) {
    public record AccountFact(String accountType, String nickname, String status, String balance) {
    }

    public record CategoryAmountFact(String category, String amount) {
    }

    public record TransactionFact(String reference, String amount, String category, String description, String date) {
    }
}
