package com.bankingdemo.ledger.dto;

import com.bankingdemo.account.dto.AccountResponse;

import java.math.BigDecimal;
import java.util.List;

public record CustomerDashboardResponse(
        BigDecimal totalBalance,
        List<AccountResponse> accounts,
        List<TransactionHistoryResponse> recentTransactions,
        long unreadNotifications
) {
}
