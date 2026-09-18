package com.bankingdemo.account.dto;

import com.bankingdemo.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        String nickname,
        String status,
        BigDecimal balance,
        String currency,
        Instant createdAt,
        Instant closedAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getAccountNumber(), a.getNickname(),
                a.getStatus().name(), a.getBalance(), a.getCurrency(), a.getCreatedAt(), a.getClosedAt());
    }
}
