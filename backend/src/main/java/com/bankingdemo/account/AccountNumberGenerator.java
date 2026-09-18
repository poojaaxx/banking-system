package com.bankingdemo.account;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates internal, fictional 12-digit account numbers. These do not
 * represent real bank accounts and are never used as an authorization
 * credential -- knowing an account number only ever allows looking up minimal
 * public display info for transfers, never any authenticated action.
 * <p>
 * Format: 1 leading digit (never the reserved SYSTEM prefix "9") + 10 random
 * digits + 1 Luhn check digit for typo detection. Uniqueness is enforced by
 * the caller retrying against the database's unique constraint, not by this
 * generator alone.
 */
@Component
public class AccountNumberGenerator {

    private static final int LENGTH_WITHOUT_CHECK_DIGIT = 11;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH_WITHOUT_CHECK_DIGIT);
        // First digit: 1-8, never 0 (avoids ambiguity) and never 9 (reserved for SYSTEM accounts).
        sb.append(1 + random.nextInt(8));
        for (int i = 1; i < LENGTH_WITHOUT_CHECK_DIGIT; i++) {
            sb.append(random.nextInt(10));
        }
        String base = sb.toString();
        return base + luhnCheckDigit(base);
    }

    private int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubleDigit = true; // rightmost digit of the base is doubled first
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }
}
