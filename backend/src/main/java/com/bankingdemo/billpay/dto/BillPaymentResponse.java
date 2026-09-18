package com.bankingdemo.billpay.dto;

import com.bankingdemo.billpay.BillPayment;

import java.math.BigDecimal;
import java.time.Instant;

public record BillPaymentResponse(
        Long id, Long financialTransactionId, Long billerId, Long accountId,
        BigDecimal amount, String referenceNote, Instant createdAt
) {
    public static BillPaymentResponse from(BillPayment p) {
        return new BillPaymentResponse(p.getId(), p.getFinancialTransactionId(), p.getBillerId(), p.getAccountId(),
                p.getAmount(), p.getReferenceNote(), p.getCreatedAt());
    }
}
