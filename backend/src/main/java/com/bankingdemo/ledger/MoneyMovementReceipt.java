package com.bankingdemo.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response returned for every completed money movement, and exactly what
 * gets stored as the idempotency response snapshot for replay. Balances are
 * only populated for the side(s) the calling customer actually owns -- a
 * transfer sender never learns the recipient's resulting balance.
 */
public record MoneyMovementReceipt(
        String reference,
        TransactionType type,
        BigDecimal amount,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter,
        String description,
        Instant createdAt
) {
}
