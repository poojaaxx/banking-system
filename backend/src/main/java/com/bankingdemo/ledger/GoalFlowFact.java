package com.bankingdemo.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/** One TRANSFER-type ledger movement on a savings-goal account (simulated deposits/withdrawals are excluded). */
public record GoalFlowFact(Instant createdAt, LedgerDirection direction, BigDecimal amount) {
}
