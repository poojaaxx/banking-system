package com.bankingdemo.adminapi.dto;

import com.bankingdemo.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminAccountSummary(
        Long id, String accountNumber, Long ownerCustomerId, String nickname,
        String status, BigDecimal balance, String frozenReason, Instant createdAt
) {
    public static AdminAccountSummary from(Account a) {
        return new AdminAccountSummary(a.getId(), a.getAccountNumber(), a.getOwnerCustomerId(), a.getNickname(),
                a.getStatus().name(), a.getBalance(), a.getFrozenReason(), a.getCreatedAt());
    }
}
