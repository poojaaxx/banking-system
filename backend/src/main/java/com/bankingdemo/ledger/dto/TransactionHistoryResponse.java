package com.bankingdemo.ledger.dto;

import com.bankingdemo.ledger.LedgerDirection;
import com.bankingdemo.ledger.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryResponse(
        Long id,
        String reference,
        TransactionType type,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Long categoryId,
        Instant createdAt,
        String counterpartyAccountNumber,
        String counterpartyDisplayName,
        String suggestedCategoryCode,
        String suggestedCategoryName
) {
}
