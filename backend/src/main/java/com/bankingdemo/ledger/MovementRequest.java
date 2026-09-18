package com.bankingdemo.ledger;

import java.math.BigDecimal;

/**
 * Internal instruction handed from {@link LedgerService} to {@link LedgerEngine}
 * once all pre-checks (ownership, demo limits, request shape) have passed.
 * {@code debitCategoryId} is attached to the debit-side ledger entry only,
 * for spending/budget aggregation.
 */
record MovementRequest(
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        TransactionType type,
        Long initiatedByCustomerId,
        String description,
        Long debitCategoryId
) {
}
