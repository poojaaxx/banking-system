package com.bankingdemo.adminapi.dto;

import com.bankingdemo.alert.AccountAlert;

import java.time.Instant;

public record AdminAlertSummary(
        Long id, String ruleCode, Long accountId, Long customerId, Long financialTransactionId,
        String severity, String message, Instant createdAt, Instant acknowledgedAt, Long acknowledgedByAdminId
) {
    public static AdminAlertSummary from(AccountAlert a) {
        return new AdminAlertSummary(a.getId(), a.getRuleCode(), a.getAccountId(), a.getCustomerId(),
                a.getFinancialTransactionId(), a.getSeverity().name(), a.getMessage(), a.getCreatedAt(),
                a.getAcknowledgedAt(), a.getAcknowledgedByAdminId());
    }
}
