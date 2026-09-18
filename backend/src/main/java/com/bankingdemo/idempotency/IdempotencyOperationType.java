package com.bankingdemo.idempotency;

public enum IdempotencyOperationType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    BILL_PAYMENT,
    REQUEST_MONEY_ACCEPT
}
