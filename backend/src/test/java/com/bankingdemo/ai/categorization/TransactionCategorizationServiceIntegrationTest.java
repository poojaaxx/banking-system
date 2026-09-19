package com.bankingdemo.ai.categorization;

import com.bankingdemo.TestcontainersConfiguration;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.ai.AiChatClient;
import com.bankingdemo.ai.AiTestConfig;
import com.bankingdemo.ai.FakeAiChatClient;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerService;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.LedgerDirection;
import com.bankingdemo.ledger.LedgerEntry;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MySQL tests for on-demand AI/rule-based categorization: ownership
 * enforcement (a customer must never be able to categorize or read a
 * suggestion for someone else's transaction), and rejecting a model reply
 * that names a category outside the fixed allowed set.
 */
@Import({TestcontainersConfiguration.class, AiTestConfig.class})
@SpringBootTest
@ActiveProfiles("test")
class TransactionCategorizationServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;
    @Autowired
    private TransactionCategorizationService categorizationService;
    @Autowired
    private SpendingCategoryRepository spendingCategoryRepository;
    @Autowired
    private AiChatClient aiChatClient;

    private Long customerAId;
    private Long customerBId;
    private Long debitLedgerEntryIdForA;

    @BeforeEach
    void setUp() {
        Customer a = customerService.register("Alice Cat", uniqueEmail(), uniqueUsername(), "correct-horse-battery-1");
        Customer b = customerService.register("Bob Cat", uniqueEmail(), uniqueUsername(), "correct-horse-battery-2");
        customerAId = a.getId();
        customerBId = b.getId();

        Long accountAId = accountService.create(customerAId, "Alice Main").getId();
        ledgerService.deposit(customerAId, accountAId, new BigDecimal("5000.00"), "Initial funding", newKey());
        MoneyMovementReceipt receipt = ledgerService.withdraw(customerAId, accountAId, new BigDecimal("250.00"), "Uber ride home", newKey());

        FinancialTransaction transaction = financialTransactionRepository.findByReference(receipt.reference()).orElseThrow();
        debitLedgerEntryIdForA = ledgerEntryRepository.findByFinancialTransactionId(transaction.getId()).stream()
                .filter(e -> e.getDirection() == LedgerDirection.DEBIT)
                .findFirst()
                .map(LedgerEntry::getId)
                .orElseThrow();

        ((FakeAiChatClient) aiChatClient).setAvailable(true);
    }

    @Test
    void ruleBasedSuggestion_matchesKeywordFromDescription() {
        CategorySuggestion suggestion = categorizationService.ruleBasedSuggestion(customerAId, debitLedgerEntryIdForA);
        assertThat(suggestion).isNotNull();
        assertThat(suggestion.categoryCode()).isEqualTo("TRANSPORT");
        assertThat(suggestion.source()).isEqualTo(CategorySuggestionSource.RULE_BASED);
    }

    @Test
    void otherCustomerCannotReadOrRecategorizeThisTransaction() {
        assertThatThrownBy(() -> categorizationService.ruleBasedSuggestion(customerBId, debitLedgerEntryIdForA))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> categorizationService.recordChoice(customerBId, debitLedgerEntryIdForA, 1L, "CUSTOMER_MANUAL"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aiSuggestionOutsideAllowedCategorySet_isDiscarded() {
        FakeAiChatClient fake = (FakeAiChatClient) aiChatClient;
        fake.respondWith("{\"categoryCode\": \"DROP_ALL_TABLES\", \"confidence\": 0.99}");

        CategorySuggestion suggestion = categorizationService.aiSuggestion(customerAId, debitLedgerEntryIdForA);

        assertThat(suggestion).isNull();
    }

    @Test
    void validAiSuggestion_isAccepted() {
        FakeAiChatClient fake = (FakeAiChatClient) aiChatClient;
        fake.respondWith("{\"categoryCode\": \"TRANSPORT\", \"confidence\": 0.9}");

        CategorySuggestion suggestion = categorizationService.aiSuggestion(customerAId, debitLedgerEntryIdForA);

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.categoryCode()).isEqualTo("TRANSPORT");
        assertThat(suggestion.source()).isEqualTo(CategorySuggestionSource.AI);
    }

    @Test
    void customerAcceptingASuggestion_isRecordedAsOwnChoice() {
        Long transportCategoryId = spendingCategoryRepository.findByCode("TRANSPORT").orElseThrow().getId();

        categorizationService.recordChoice(customerAId, debitLedgerEntryIdForA, transportCategoryId, "CUSTOMER_ACCEPTED_RULE");

        LedgerEntry entry = ledgerEntryRepository.findById(debitLedgerEntryIdForA).orElseThrow();
        assertThat(entry.getCategoryId()).isEqualTo(transportCategoryId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String uniqueUsername() {
        return "catuser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String uniqueEmail() {
        return "cattest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.invalid";
    }
}
