package com.bankingdemo.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-model row for one account's transaction history: one ledger entry
 * joined with its parent transaction. Counterparty account display info is
 * resolved separately (batched) by the service layer to avoid embedding an
 * unmapped three-way join here.
 */
public record TransactionHistoryRow(
        Long ledgerEntryId,
        Long financialTransactionId,
        String reference,
        TransactionType type,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Long categoryId,
        Instant createdAt,
        Long sourceAccountId,
        Long destinationAccountId
) {
}
