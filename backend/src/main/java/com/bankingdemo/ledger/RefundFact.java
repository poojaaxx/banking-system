package com.bankingdemo.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A credit to a customer from another customer's account whose description
 * says "refund" or "reversal". The ledger has no first-class refund type, so
 * this description match is the documented, deliberately conservative rule
 * for netting refunds against spending (see docs/insights.md).
 */
public record RefundFact(Long transactionId, BigDecimal amount, Instant createdAt) {
}
