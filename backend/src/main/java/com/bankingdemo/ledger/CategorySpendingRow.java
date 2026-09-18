package com.bankingdemo.ledger;

import java.math.BigDecimal;

public record CategorySpendingRow(Long categoryId, BigDecimal totalSpent) {
}
