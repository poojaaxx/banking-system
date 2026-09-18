package com.bankingdemo.adminapi.dto;

import java.math.BigDecimal;

public record AdminDashboardSummary(
        long totalCustomers,
        long totalCustomerAccounts,
        BigDecimal totalCustomerBalance,
        long totalTransactions,
        long openSupportTickets,
        long unacknowledgedAlerts
) {
}
