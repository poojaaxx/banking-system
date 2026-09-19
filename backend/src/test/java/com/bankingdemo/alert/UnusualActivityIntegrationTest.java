package com.bankingdemo.alert;

import com.bankingdemo.TestcontainersConfiguration;
import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.account.AccountStatus;
import com.bankingdemo.ai.AiTestConfig;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerService;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.ledger.SpendingFact;
import com.bankingdemo.notification.Notification;
import com.bankingdemo.notification.NotificationRepository;
import com.bankingdemo.notification.RecipientType;
import com.bankingdemo.notification.sse.SseEventPublisher;
import com.bankingdemo.testsupport.HistorySeeder;
import com.bankingdemo.testsupport.RecordingSseEventPublisher;
import com.bankingdemo.testsupport.SwitchableDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-MySQL tests for the customer-specific unusual-activity checks.
 * They assert both what is flagged and, just as importantly, what is not:
 * cold starts, normal payments, own-account transfers, deposits, other
 * customers' history, and duplicates. Alerts only ever inform -- nothing here
 * may freeze an account or block a payment.
 */
@Import({TestcontainersConfiguration.class, AiTestConfig.class})
@SpringBootTest
@ActiveProfiles("test")
class UnusualActivityIntegrationTest {

    @Autowired private CustomerService customerService;
    @Autowired private AccountService accountService;
    @Autowired private LedgerService ledgerService;
    @Autowired private HistorySeeder seeder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountAlertRepository alertRepository;
    @Autowired private AlertService alertService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private SseEventPublisher sseEventPublisher;
    @Autowired private UnusualActivityDetector detector;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long aId;
    private Long bId;
    private Account aMain;
    private Account bMain;

    @BeforeEach
    void setUp() {
        Customer a = customerService.register("Alice Unusual", email(), username(), "correct-horse-battery-1");
        Customer b = customerService.register("Bob Unusual", email(), username(), "correct-horse-battery-2");
        aId = a.getId();
        bId = b.getId();
        aMain = accountService.create(aId, "A main");
        bMain = accountService.create(bId, "B main");
        ledgerService.deposit(aId, aMain.getId(), new BigDecimal("50000.00"), "Initial funding", key());
        ledgerService.deposit(bId, bMain.getId(), new BigDecimal("50000.00"), "Initial funding", key());
        ((RecordingSseEventPublisher) sseEventPublisher).clear();
        ((SwitchableDetector) detector).setFailing(false);
    }

    @AfterEach
    void tearDown() {
        ((SwitchableDetector) detector).setFailing(false);
    }

    /** {@code count} withdrawals, one per day going back from two days ago, of {@code base}, {@code base+10}, ... */
    private void seedHistory(Long customerId, Account account, int count, int base) {
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            seeder.withdrawal(customerId, account, BigDecimal.valueOf(base + i * 10L), now.minus(Duration.ofDays(i + 2)), "History " + i, null);
        }
    }

    private List<AccountAlert> alerts(Long customerId, String rule) {
        return alertRepository.findByCustomerIdAndRuleCodeInOrderByCreatedAtDesc(customerId, List.of(rule), PageRequest.of(0, 50));
    }

    private List<Notification> unusualNotifications(Long customerId) {
        return notificationRepository.findByRecipientTypeAndRecipientIdOrderByIdDesc(RecipientType.CUSTOMER, customerId, PageRequest.of(0, 100))
                .getContent().stream().filter(n -> "UNUSUAL_ACTIVITY".equals(n.getType())).toList();
    }

    @Test
    void coldStartNeverFlagsAnything() {
        seedHistory(aId, aMain, 4, 100); // fewer than the 5 prior payments needed for a baseline
        ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("9000.00"), "Big purchase", key());
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();
        assertThat(unusualNotifications(aId)).isEmpty();
    }

    @Test
    void aPaymentInsideTheCustomersUsualRangeIsNotFlagged() {
        seedHistory(aId, aMain, 10, 1500); // 1500..1590, median ~1545
        ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("2600.00"), "Slightly bigger", key());
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();
    }

    @Test
    void aPaymentFarAboveTheCustomersOwnPatternIsFlaggedWithAnExplanationAndNothingIsBlocked() {
        seedHistory(aId, aMain, 10, 1500);
        MoneyMovementReceipt receipt = ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("9000.00"), "Big purchase", key());

        List<AccountAlert> flagged = alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE);
        assertThat(flagged).hasSize(1);
        AccountAlert alert = flagged.get(0);
        assertThat(alert.getMessage())
                .contains("Unusual activity (statistical check)")
                .contains("typical payment (median)")
                .contains("cutoff")
                .contains("not a finding of fraud")
                .contains("nothing was blocked")
                .doesNotContainIgnoringCase("ai fraud");
        assertThat(alert.getFinancialTransactionId()).isNotNull();

        // Informational only: the payment went through and the account is untouched by the alert.
        Account after = accountRepository.findById(aMain.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(after.getBalance()).isEqualByComparingTo("41000.00"); // 50000 - 9000, history rows never touched balances
        assertThat(transactionRepository.findByReference(receipt.reference())).isPresent();

        // The owner is told, through the existing notification + SSE path, and nobody else is.
        assertThat(unusualNotifications(aId)).hasSize(1);
        assertThat(unusualNotifications(bId)).isEmpty();
        RecordingSseEventPublisher sse = (RecordingSseEventPublisher) sseEventPublisher;
        assertThat(sse.publishedTo(aId)).anyMatch(p -> "UNUSUAL_ACTIVITY".equals(p.type()));
        assertThat(sse.publishedTo(bId)).noneMatch(p -> "UNUSUAL_ACTIVITY".equals(p.type()));
    }

    @Test
    void theBaselineOnlyUsesInformationThatExistedBeforeTheTransaction() {
        seedHistory(aId, aMain, 10, 1500);
        MoneyMovementReceipt receipt = ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("9000.00"), "Big purchase", key());
        FinancialTransaction tx = transactionRepository.findByReference(receipt.reference()).orElseThrow();
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).hasSize(1);

        // Afterwards the customer starts making many payments of 9000. With hindsight the median would be 9000
        // and the original payment would look ordinary; a baseline that leaked future data would not flag it.
        Instant later = tx.getCreatedAt().plusSeconds(3600);
        for (int i = 0; i < 25; i++) {
            seeder.withdrawal(aId, aMain, new BigDecimal("9000.00"), later.plusSeconds(i), "Later habit", null);
        }
        alertRepository.deleteAll(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE));
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();

        transactionTemplate.executeWithoutResult(status -> detector.evaluate(tx));

        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).as("replay judges the payment as of when it happened").hasSize(1);

        List<SpendingFact> visible = ledgerEntryRepository.spendingBetween(aId, tx.getCreatedAt().minus(Duration.ofDays(90)),
                tx.getCreatedAt(), tx.getId(), PageRequest.of(0, 1000));
        assertThat(visible).hasSize(10);
        assertThat(visible).noneMatch(f -> f.description() != null && f.description().equals("Later habit"));
        assertThat(visible).noneMatch(f -> f.transactionId().equals(tx.getId()));
    }

    @Test
    void anotherCustomersHistoryNeverInfluencesTheBaseline() {
        seedHistory(aId, aMain, 10, 100);     // Alice usually pays ~100-190
        seedHistory(bId, bMain, 10, 5000);    // Bob usually pays ~5000-5090

        ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("5000.00"), "Alice's big one", key());
        ledgerService.withdraw(bId, bMain.getId(), new BigDecimal("5000.00"), "Bob's normal one", key());

        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).hasSize(1);
        assertThat(alerts(bId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();
        assertThat(alertService.listUnusualActivityForCustomer(bId, 20)).isEmpty();
        assertThat(alertService.listUnusualActivityForCustomer(aId, 20)).hasSize(1);
        assertThat(unusualNotifications(bId)).isEmpty();
    }

    @Test
    void repeatedIdenticalPaymentsRaiseOneAlertNotOnePerPayment() {
        String recipient = bMain.getAccountNumber();
        for (int i = 0; i < 5; i++) {
            ledgerService.transfer(aId, aMain.getId(), recipient, new BigDecimal("300.00"), "Rent share", null, key());
        }

        List<AccountAlert> repeated = alerts(aId, UnusualActivityDetector.REPEATED_PAYMENT_RULE);
        assertThat(repeated).hasSize(1);
        assertThat(repeated.get(0).getMessage())
                .contains("Unusual activity (repeat-payment check)")
                .contains("3 identical payments")
                .contains("not a finding of fraud")
                .contains("nothing was blocked");
        assertThat(unusualNotifications(aId)).hasSize(1);
        assertThat(accountRepository.findById(aMain.getId()).orElseThrow().getBalance()).isEqualByComparingTo("48500.00");
        assertThat(alerts(bId, UnusualActivityDetector.REPEATED_PAYMENT_RULE)).isEmpty();
    }

    @Test
    void aDifferentAmountOrRecipientDoesNotCountAsARepeat() {
        String recipient = bMain.getAccountNumber();
        ledgerService.transfer(aId, aMain.getId(), recipient, new BigDecimal("300.00"), "One", null, key());
        ledgerService.transfer(aId, aMain.getId(), recipient, new BigDecimal("301.00"), "Two", null, key());
        ledgerService.transfer(aId, aMain.getId(), recipient, new BigDecimal("302.00"), "Three", null, key());
        assertThat(alerts(aId, UnusualActivityDetector.REPEATED_PAYMENT_RULE)).isEmpty();
    }

    @Test
    void movingMoneyBetweenYourOwnAccountsIsNeverFlagged() {
        Account aSecond = accountService.create(aId, "A second");
        for (int i = 0; i < 5; i++) {
            ledgerService.transfer(aId, aMain.getId(), aSecond.getAccountNumber(), new BigDecimal("300.00"), "Own move", null, key());
        }
        assertThat(alerts(aId, UnusualActivityDetector.REPEATED_PAYMENT_RULE)).isEmpty();
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();
    }

    @Test
    void simulatedFundingIsNeverFlagged() {
        ledgerService.deposit(aId, aMain.getId(), new BigDecimal("90000.00"), "More funding", key());
        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).isEmpty();
        assertThat(alerts(aId, UnusualActivityDetector.REPEATED_PAYMENT_RULE)).isEmpty();
    }

    @Test
    void evaluatingTheSameTransactionTwiceNeverDuplicatesTheAlertOrTheNotification() {
        seedHistory(aId, aMain, 10, 1500);
        MoneyMovementReceipt receipt = ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("9000.00"), "Big purchase", key());
        FinancialTransaction tx = transactionRepository.findByReference(receipt.reference()).orElseThrow();

        transactionTemplate.executeWithoutResult(status -> detector.evaluate(tx));
        transactionTemplate.executeWithoutResult(status -> detector.evaluate(tx));

        assertThat(alerts(aId, UnusualActivityDetector.LARGE_SPEND_RULE)).hasSize(1);
        assertThat(unusualNotifications(aId)).hasSize(1);
    }

    @Test
    void aBugInTheDetectorCanNeverFailOrRollBackAMoneyMovement() {
        ((SwitchableDetector) detector).setFailing(true);

        MoneyMovementReceipt receipt = ledgerService.withdraw(aId, aMain.getId(), new BigDecimal("700.00"), "Still goes through", key());

        assertThat(transactionRepository.findByReference(receipt.reference())).isPresent();
        assertThat(accountRepository.findById(aMain.getId()).orElseThrow().getBalance()).isEqualByComparingTo("49300.00");
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String username() {
        return "ua_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String email() {
        return "ua_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.invalid";
    }
}
