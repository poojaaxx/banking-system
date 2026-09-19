package com.bankingdemo.alert;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.insights.stats.SpendingStatistics;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.SpendingFact;
import com.bankingdemo.ledger.TransactionType;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.RecipientType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Customer-specific unusual-activity checks. These are plain statistics and
 * counting over the customer's OWN earlier payments -- not AI, and never
 * described as fraud detection. An alert here is a prompt to look, nothing
 * more: this class only writes an alert row and a notification; it never
 * freezes an account, blocks or reverses a transaction, or moves money.
 *
 * Baselines use only information that existed before the transaction being
 * judged (rows strictly older by time AND with a smaller id), so a check
 * gives the same answer whether it runs live or is replayed later.
 *
 * Documented thresholds (see docs/insights.md and the alert_rules table):
 *  UNUSUAL_LARGE_SPEND  needs >= threshold_count prior payments in the last
 *    window_minutes (90 days); flags a payment that is >= threshold_amount
 *    AND above both the Tukey far-out fence (Q3 + 3*IQR) and 3x the median of
 *    those prior payments.
 *  REPEATED_PAYMENT     flags the payment that makes >= threshold_count
 *    identical payments (same recipient, same amount) within window_minutes
 *    (24 h), at most once per such cluster.
 */
@Component
public class UnusualActivityDetector {

    public static final String LARGE_SPEND_RULE = "UNUSUAL_LARGE_SPEND";
    public static final String REPEATED_PAYMENT_RULE = "REPEATED_PAYMENT";

    static final double TUKEY_FAR_OUT_K = 3.0;
    static final BigDecimal MEDIAN_MULTIPLE = BigDecimal.valueOf(3);
    private static final int MAX_BASELINE_ROWS = 1000;

    private final AlertRuleRepository alertRuleRepository;
    private final AccountAlertRepository accountAlertRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    public UnusualActivityDetector(AlertRuleRepository alertRuleRepository,
                                    AccountAlertRepository accountAlertRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    AccountRepository accountRepository,
                                    NotificationService notificationService) {
        this.alertRuleRepository = alertRuleRepository;
        this.accountAlertRepository = accountAlertRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    /** Runs inside the money-movement transaction; the transaction row must already be flushed (id + createdAt set). */
    public void evaluate(FinancialTransaction tx) {
        if (!isCustomerSpending(tx)) {
            return;
        }
        checkLargeSpend(tx);
        checkRepeatedPayment(tx);
    }

    private boolean isCustomerSpending(FinancialTransaction tx) {
        if (tx.getType() == TransactionType.DEPOSIT || tx.getCreatedAt() == null) {
            return false;
        }
        Account source = accountRepository.findById(tx.getSourceAccountId()).orElse(null);
        Account destination = accountRepository.findById(tx.getDestinationAccountId()).orElse(null);
        if (source == null || destination == null || source.isSystem()
                || source.getOwnerCustomerId() == null
                || !source.getOwnerCustomerId().equals(tx.getInitiatedByCustomerId())) {
            return false;
        }
        // Moving money between one's own accounts (incl. savings goals) is not spending.
        return !(tx.getType() == TransactionType.TRANSFER
                && source.getOwnerCustomerId().equals(destination.getOwnerCustomerId()));
    }

    private void checkLargeSpend(FinancialTransaction tx) {
        AlertRule rule = alertRuleRepository.findByCode(LARGE_SPEND_RULE).filter(AlertRule::isEnabled).orElse(null);
        if (rule == null || rule.getThresholdCount() == null || rule.getWindowMinutes() == null || rule.getThresholdAmount() == null) {
            return;
        }
        if (tx.getAmount().compareTo(rule.getThresholdAmount()) < 0) {
            return;
        }
        Instant asOf = tx.getCreatedAt();
        List<SpendingFact> prior = ledgerEntryRepository.spendingBetween(tx.getInitiatedByCustomerId(),
                asOf.minus(Duration.ofMinutes(rule.getWindowMinutes())), asOf, tx.getId(),
                PageRequest.of(0, MAX_BASELINE_ROWS));
        if (prior.size() < rule.getThresholdCount()) {
            return; // not enough history to say what "unusual" means for this customer
        }
        List<BigDecimal> amounts = prior.stream().map(SpendingFact::amount).toList();
        BigDecimal median = SpendingStatistics.median(amounts);
        BigDecimal fence = SpendingStatistics.tukeyUpperFence(amounts, TUKEY_FAR_OUT_K);
        BigDecimal cutoff = SpendingStatistics.max(fence, median.multiply(MEDIAN_MULTIPLE));
        if (tx.getAmount().compareTo(cutoff) <= 0) {
            return;
        }

        String message = "Unusual activity (statistical check): a payment of ₹" + tx.getAmount().toPlainString()
                + " (" + tx.getReference() + ") is well above your usual range. Your typical payment (median) was ₹"
                + SpendingStatistics.money(median).toPlainString() + " and the unusually-large cutoff was ₹"
                + SpendingStatistics.money(cutoff).toPlainString() + ", based on your " + prior.size()
                + " payments in the previous " + Duration.ofMinutes(rule.getWindowMinutes()).toDays()
                + " days. This is an automated statistical flag for you to review, not a finding of fraud; nothing was blocked.";
        raise(rule.getCode(), tx, message, "tx:" + tx.getId(),
                "Unusual activity noticed",
                "A ₹" + tx.getAmount().toPlainString() + " payment is well above your recent spending pattern. "
                        + "Open Insights to see why. Automated check — nothing was blocked.");
    }

    private void checkRepeatedPayment(FinancialTransaction tx) {
        if (tx.getType() != TransactionType.TRANSFER && tx.getType() != TransactionType.BILL_PAYMENT) {
            return;
        }
        AlertRule rule = alertRuleRepository.findByCode(REPEATED_PAYMENT_RULE).filter(AlertRule::isEnabled).orElse(null);
        if (rule == null || rule.getThresholdCount() == null || rule.getWindowMinutes() == null) {
            return;
        }
        Instant asOf = tx.getCreatedAt();
        Duration window = Duration.ofMinutes(rule.getWindowMinutes());
        long priorIdentical = ledgerEntryRepository.spendingBetween(tx.getInitiatedByCustomerId(),
                        asOf.minus(window), asOf, tx.getId(), PageRequest.of(0, MAX_BASELINE_ROWS)).stream()
                .filter(f -> f.destinationAccountId().equals(tx.getDestinationAccountId()))
                .filter(f -> f.amount().compareTo(tx.getAmount()) == 0)
                .filter(f -> f.type() == TransactionType.TRANSFER || f.type() == TransactionType.BILL_PAYMENT)
                .count();
        long total = priorIdentical + 1;
        if (total < rule.getThresholdCount()) {
            return;
        }

        String clusterPrefix = "dest:" + tx.getDestinationAccountId() + "|amt:" + tx.getAmount().toPlainString() + "|";
        if (accountAlertRepository.existsByRuleCodeAndCustomerIdAndDedupeKeyStartingWithAndCreatedAtAfter(
                rule.getCode(), tx.getInitiatedByCustomerId(), clusterPrefix, asOf.minus(window))) {
            return; // this cluster of repeats was already reported
        }
        String message = "Unusual activity (repeat-payment check): " + total + " identical payments of ₹"
                + tx.getAmount().toPlainString() + " to the same recipient within "
                + window.toHours() + " hours, the latest being " + tx.getReference()
                + ". If you meant to make these, no action is needed. This is an automated rule, not a finding of fraud; nothing was blocked.";
        raise(rule.getCode(), tx, message, clusterPrefix + "tx:" + tx.getId(),
                "Repeated payments noticed",
                total + " identical payments of ₹" + tx.getAmount().toPlainString()
                        + " to the same recipient in " + window.toHours() + " hours. Automated check — nothing was blocked.");
    }

    private void raise(String ruleCode, FinancialTransaction tx, String message, String dedupeKey,
                       String notificationTitle, String notificationBody) {
        int inserted = accountAlertRepository.insertIgnore(ruleCode, tx.getSourceAccountId(), tx.getInitiatedByCustomerId(),
                tx.getId(), AlertSeverity.MEDIUM.name(), truncate(message, 500),
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), dedupeKey);
        if (inserted == 1) {
            // Delivered to the owning customer only; the SSE push happens after this transaction commits.
            notificationService.create(RecipientType.CUSTOMER, tx.getInitiatedByCustomerId(), "UNUSUAL_ACTIVITY",
                    notificationTitle, notificationBody, "ACCOUNT", tx.getSourceAccountId());
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
