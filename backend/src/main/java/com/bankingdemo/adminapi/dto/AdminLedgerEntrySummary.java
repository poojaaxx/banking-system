package com.bankingdemo.adminapi.dto;

import com.bankingdemo.ledger.LedgerEntry;

import java.math.BigDecimal;

public record AdminLedgerEntrySummary(Long accountId, String direction, BigDecimal amount, BigDecimal balanceAfter) {
    public static AdminLedgerEntrySummary from(LedgerEntry e) {
        return new AdminLedgerEntrySummary(e.getAccountId(), e.getDirection().name(), e.getAmount(), e.getBalanceAfter());
    }
}
