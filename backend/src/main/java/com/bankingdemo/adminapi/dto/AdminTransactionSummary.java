package com.bankingdemo.adminapi.dto;

import com.bankingdemo.ledger.FinancialTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTransactionSummary(
        Long id, String reference, String type, BigDecimal amount, Long initiatedByCustomerId,
        Long sourceAccountId, Long destinationAccountId, String description, Instant createdAt
) {
    public static AdminTransactionSummary from(FinancialTransaction t) {
        return new AdminTransactionSummary(t.getId(), t.getReference(), t.getType().name(), t.getAmount(),
                t.getInitiatedByCustomerId(), t.getSourceAccountId(), t.getDestinationAccountId(),
                t.getDescription(), t.getCreatedAt());
    }
}
