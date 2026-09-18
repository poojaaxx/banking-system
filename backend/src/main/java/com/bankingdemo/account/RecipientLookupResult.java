package com.bankingdemo.account;

/** Minimal, non-sensitive info shown when a customer looks up a transfer recipient by account number. */
public record RecipientLookupResult(String accountNumber, String displayName, String nickname) {
}
