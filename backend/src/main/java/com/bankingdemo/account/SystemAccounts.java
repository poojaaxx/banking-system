package com.bankingdemo.account;

/**
 * Account numbers seeded by V1__core_identities_and_accounts.sql. These are the
 * only two SYSTEM accounts in the ledger and represent "the outside world" for
 * simulated deposits/withdrawals (SYSTEM_CASH) and fictional bill payments
 * (SYSTEM_BILLPAY). They are never returned by recipient search and never
 * counted in customer balance totals.
 */
public final class SystemAccounts {

    public static final String CASH_ACCOUNT_NUMBER = "900000000001";
    public static final String BILLPAY_ACCOUNT_NUMBER = "900000000002";

    /** Reserved prefix: customer account numbers must never start with this. */
    public static final String RESERVED_PREFIX = "9";

    private SystemAccounts() {
    }
}
