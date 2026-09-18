package com.bankingdemo.moneyrequest.dto;

import com.bankingdemo.moneyrequest.MoneyRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record MoneyRequestResponse(
        Long id, Long requesterCustomerId, Long requesterAccountId, Long payerCustomerId,
        BigDecimal amount, String note, String status, Long financialTransactionId,
        Instant createdAt, Instant respondedAt
) {
    public static MoneyRequestResponse from(MoneyRequest r) {
        return new MoneyRequestResponse(r.getId(), r.getRequesterCustomerId(), r.getRequesterAccountId(),
                r.getPayerCustomerId(), r.getAmount(), r.getNote(), r.getStatus().name(),
                r.getFinancialTransactionId(), r.getCreatedAt(), r.getRespondedAt());
    }
}
