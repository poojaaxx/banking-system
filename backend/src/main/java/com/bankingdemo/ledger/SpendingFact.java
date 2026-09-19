package com.bankingdemo.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One outgoing payment by a customer that counts as "spending": any debit from
 * one of their accounts except a transfer between two of their own accounts
 * (which includes contributions to their own savings-goal accounts). Simulated
 * deposits are credits, so they never appear here.
 */
public record SpendingFact(
        Long transactionId,
        String reference,
        TransactionType type,
        BigDecimal amount,
        Instant createdAt,
        Long destinationAccountId,
        Long categoryId,
        String description
) {
}
