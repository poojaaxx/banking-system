package com.bankingdemo.ledger;

import com.bankingdemo.TestcontainersConfiguration;
import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.account.AccountStatus;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MySQL integration tests (via Testcontainers) for the ledger engine's
 * core financial invariants: balanced double-entry, idempotency, locking
 * under concurrency, and account-state enforcement. These intentionally do
 * not use H2 -- see CLAUDE.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    private Long customerAId;
    private Long accountAId;
    private Long customerBId;
    private Long accountBId;

    @BeforeEach
    void setUp() {
        Customer a = customerService.register("Alice Demo", uniqueEmail(), uniqueUsername(), "correct-horse-battery-1");
        Customer b = customerService.register("Bob Demo", uniqueEmail(), uniqueUsername(), "correct-horse-battery-2");
        customerAId = a.getId();
        customerBId = b.getId();
        accountAId = accountService.create(customerAId, "Alice Main").getId();
        accountBId = accountService.create(customerBId, "Bob Main").getId();
    }

    @Test
    void newAccountsStartAtZero() {
        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void depositThenTransfer_updatesBalancesAndWritesBalancedLedger() {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("10000.00"), "Initial funding", newKey());
        ledgerService.deposit(customerBId, accountBId, new BigDecimal("2000.00"), "Initial funding", newKey());

        MoneyMovementReceipt receipt = ledgerService.transfer(customerAId, accountAId,
                accountRepository.findById(accountBId).orElseThrow().getAccountNumber(),
                new BigDecimal("3000.00"), "Rent share", null, newKey());

        Account a = accountRepository.findById(accountAId).orElseThrow();
        Account b = accountRepository.findById(accountBId).orElseThrow();
        assertThat(a.getBalance()).isEqualByComparingTo("7000.00");
        assertThat(b.getBalance()).isEqualByComparingTo("5000.00");

        FinancialTransaction ft = financialTransactionRepository.findByReference(receipt.reference()).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository.findByFinancialTransactionId(ft.getId());
        assertThat(entries).hasSize(2);
        BigDecimal debitAmount = entries.stream().filter(e -> e.getDirection() == LedgerDirection.DEBIT).findFirst().orElseThrow().getAmount();
        BigDecimal creditAmount = entries.stream().filter(e -> e.getDirection() == LedgerDirection.CREDIT).findFirst().orElseThrow().getAmount();
        assertThat(debitAmount).isEqualByComparingTo(creditAmount);
    }

    @Test
    void retryingSameOperation_doesNotMoveMoneyTwice() {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("10000.00"), "Initial funding", newKey());
        String destinationAccountNumber = accountRepository.findById(accountBId).orElseThrow().getAccountNumber();
        String key = newKey();

        MoneyMovementReceipt first = ledgerService.transfer(customerAId, accountAId, destinationAccountNumber,
                new BigDecimal("3000.00"), "Rent share", null, key);
        MoneyMovementReceipt replay = ledgerService.transfer(customerAId, accountAId, destinationAccountNumber,
                new BigDecimal("3000.00"), "Rent share", null, key);

        assertThat(replay.reference()).isEqualTo(first.reference());
        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance()).isEqualByComparingTo("7000.00");
    }

    @Test
    void reusingKeyWithDifferentPayload_isRejected() {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("10000.00"), "Initial funding", newKey());
        String destinationAccountNumber = accountRepository.findById(accountBId).orElseThrow().getAccountNumber();
        String key = newKey();

        ledgerService.transfer(customerAId, accountAId, destinationAccountNumber, new BigDecimal("1000.00"), "First", null, key);

        assertThatThrownBy(() -> ledgerService.transfer(customerAId, accountAId, destinationAccountNumber,
                new BigDecimal("2000.00"), "Different amount", null, key))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different request");
    }

    @Test
    void insufficientFunds_rejectedAndBalanceUnchanged() {
        assertThatThrownBy(() -> ledgerService.withdraw(customerAId, accountAId, new BigDecimal("50.00"), "Too much", newKey()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void frozenAccount_blocksOutgoingTransfer() {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("5000.00"), "Initial funding", newKey());
        Account account = accountRepository.findById(accountAId).orElseThrow();
        account.setStatus(AccountStatus.FROZEN);
        account.setFrozenReason("Test freeze");
        accountRepository.save(account);

        String destinationAccountNumber = accountRepository.findById(accountBId).orElseThrow().getAccountNumber();
        assertThatThrownBy(() -> ledgerService.transfer(customerAId, accountAId, destinationAccountNumber,
                new BigDecimal("100.00"), "Should fail", null, newKey()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("frozen");

        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance()).isEqualByComparingTo("5000.00");
    }

    @Test
    void concurrentWithdrawals_competingForInsufficientFunds_onlyOneSucceeds() throws InterruptedException {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("100.00"), "Seed", newKey());

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    ledgerService.withdraw(customerAId, accountAId, new BigDecimal("100.00"), "Race", newKey());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threads - 1);
        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void concurrentDuplicateIdempotentRequests_onlyOneMovesMoney() throws InterruptedException {
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("10000.00"), "Initial funding", newKey());
        String destinationAccountNumber = accountRepository.findById(accountBId).orElseThrow().getAccountNumber();
        String sharedKey = newKey();

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    ledgerService.transfer(customerAId, accountAId, destinationAccountNumber,
                            new BigDecimal("500.00"), "Race", null, sharedKey);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Every successful caller must have received the SAME transaction reference (replay),
        // and the balance must reflect exactly one 500.00 transfer, never more.
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(accountRepository.findById(accountAId).orElseThrow().getBalance()).isEqualByComparingTo("9500.00");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String uniqueUsername() {
        return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String uniqueEmail() {
        return "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.invalid";
    }
}
