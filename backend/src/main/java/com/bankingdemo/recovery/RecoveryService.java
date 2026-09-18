package com.bankingdemo.recovery;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Customer account recovery without a paid email service: single-use codes,
 * only ever shown once to the already-authenticated customer, stored only as
 * secure hashes. This is a demo fallback for "I forgot my password", not a
 * verified-email-ownership flow -- the app never claims otherwise.
 */
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_LENGTH = 10;
    // Excludes visually ambiguous characters (0/O, 1/I/L).
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private final RecoveryCodeRepository recoveryCodeRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /** Invalidates any currently active codes and issues a fresh batch. Returns the plaintext codes exactly once. */
    @Transactional
    public List<String> regenerate(Long customerId) {
        recoveryCodeRepository.invalidateAllActive(customerId, Instant.now());

        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode();
            plaintextCodes.add(code);
            RecoveryCode entity = new RecoveryCode();
            entity.setCustomerId(customerId);
            entity.setCodeHash(passwordEncoder.encode(code));
            recoveryCodeRepository.save(entity);
        }
        return plaintextCodes;
    }

    /**
     * Verifies a recovery code for the given username and, if valid, atomically
     * consumes it (so it can never be used twice, even under a concurrent
     * duplicate submission) and returns the matching customer.
     */
    @Transactional
    public Customer redeemAndLogin(String username, String code) {
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.badRequest("Invalid username or recovery code"));

        List<RecoveryCode> activeCodes = recoveryCodeRepository.findActiveByCustomerId(customer.getId());
        Optional<RecoveryCode> match = activeCodes.stream()
                .filter(rc -> passwordEncoder.matches(code, rc.getCodeHash()))
                .findFirst();

        if (match.isEmpty()) {
            throw ApiException.badRequest("Invalid username or recovery code");
        }

        int consumed = recoveryCodeRepository.consume(match.get().getId(), Instant.now());
        if (consumed != 1) {
            // Raced with another concurrent redemption of the same code.
            throw ApiException.badRequest("This recovery code has already been used");
        }
        return customer;
    }

    @Transactional(readOnly = true)
    public long countActive(Long customerId) {
        return recoveryCodeRepository.findActiveByCustomerId(customerId).size();
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            if (i == 4) {
                sb.append('-');
            }
        }
        return sb.toString();
    }
}
