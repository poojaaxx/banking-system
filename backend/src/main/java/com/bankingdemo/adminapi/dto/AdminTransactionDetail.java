package com.bankingdemo.adminapi.dto;

import java.util.List;

public record AdminTransactionDetail(AdminTransactionSummary transaction, List<AdminLedgerEntrySummary> entries) {
}
