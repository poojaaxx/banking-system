package com.bankingdemo.alert;

import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.TransactionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small set of explainable, configurable threshold rules (see alert_rules,
 * seeded in V7). Deliberately simple counting/comparisons -- never described
 * anywhere as "AI fraud detection". Failed-login tracking is an in-memory,
 * per-instance counter (like RateLimiter) for the same reasons documented
 * there: this is a single-instance demo, not a durable security guarantee.
 */
@Component
@RequiredArgsConstructor
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AccountAlertRepository accountAlertRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final UnusualActivityDetector unusualActivityDetector;

    private record FailedLoginWindow(AtomicInteger count, long windowStartMillis) {
    }

    private final ConcurrentHashMap<String, FailedLoginWindow> failedLogins = new ConcurrentHashMap<>();

    /** Called from within the same transaction that just wrote the ledger rows for this movement. */
    @Transactional
    public void evaluateTransaction(FinancialTransaction transaction) {
        alertRuleRepository.findByCode("LARGE_TRANSACTION")
                .filter(AlertRule::isEnabled)
                .filter(rule -> rule.getThresholdAmount() != null)
                .filter(rule -> transaction.getAmount().compareTo(rule.getThresholdAmount()) > 0)
                .ifPresent(rule -> raise(rule.getCode(), transaction.getSourceAccountId(),
                        transaction.getInitiatedByCustomerId(), transaction.getId(), AlertSeverity.MEDIUM,
                        "Transaction " + transaction.getReference() + " of ₹" + transaction.getAmount()
                                + " exceeds the demo large-transaction threshold"));

        if (transaction.getType() == TransactionType.TRANSFER) {
            alertRuleRepository.findByCode("RAPID_TRANSFERS")
                    .filter(AlertRule::isEnabled)
                    .filter(rule -> rule.getThresholdCount() != null && rule.getWindowMinutes() != null)
                    .ifPresent(rule -> {
                        Instant since = Instant.now().minus(Duration.ofMinutes(rule.getWindowMinutes()));
                        long recentCount = financialTransactionRepository.countBySourceAccountIdAndTypeAndCreatedAtAfter(
                                transaction.getSourceAccountId(), TransactionType.TRANSFER, since);
                        if (recentCount >= rule.getThresholdCount()) {
                            raise(rule.getCode(), transaction.getSourceAccountId(), transaction.getInitiatedByCustomerId(),
                                    transaction.getId(), AlertSeverity.MEDIUM,
                                    recentCount + " outgoing transfers from this account in the last "
                                            + rule.getWindowMinutes() + " minutes");
                        }
                    });
        }

        // Advisory statistics must never be able to fail a money movement: any unexpected error is
        // logged (class name only) and swallowed so the transfer still commits.
        try {
            unusualActivityDetector.evaluate(transaction);
        } catch (RuntimeException e) {
            log.warn("Unusual-activity check skipped due to an internal error ({})", e.getClass().getSimpleName());
        }
    }

    /** Called from the login endpoint's failure path -- never inside the authenticated transaction. */
    public void recordFailedLogin(String username, Long customerId) {
        alertRuleRepository.findByCode("REPEATED_FAILED_LOGIN")
                .filter(AlertRule::isEnabled)
                .filter(rule -> rule.getThresholdCount() != null && rule.getWindowMinutes() != null)
                .ifPresent(rule -> {
                    long now = System.currentTimeMillis();
                    long windowMillis = Duration.ofMinutes(rule.getWindowMinutes()).toMillis();
                    FailedLoginWindow window = failedLogins.compute(username, (k, existing) -> {
                        if (existing == null || now - existing.windowStartMillis() >= windowMillis) {
                            return new FailedLoginWindow(new AtomicInteger(1), now);
                        }
                        existing.count().incrementAndGet();
                        return existing;
                    });
                    if (window.count().get() == rule.getThresholdCount()) {
                        raise(rule.getCode(), null, customerId, null, AlertSeverity.HIGH,
                                rule.getThresholdCount() + " failed login attempts for '" + username + "' within "
                                        + rule.getWindowMinutes() + " minutes");
                    }
                });
    }

    private void raise(String ruleCode, Long accountId, Long customerId, Long financialTransactionId,
                        AlertSeverity severity, String message) {
        AccountAlert alert = new AccountAlert();
        alert.setRuleCode(ruleCode);
        alert.setAccountId(accountId);
        alert.setCustomerId(customerId);
        alert.setFinancialTransactionId(financialTransactionId);
        alert.setSeverity(severity);
        alert.setMessage(message);
        accountAlertRepository.save(alert);
    }
}
