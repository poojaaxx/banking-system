package com.bankingdemo.ai.assistant;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.ledger.CategorySpendingRow;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.SpendingCategory;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import com.bankingdemo.ledger.TransactionHistoryRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AssistantContextBuilder {

    private static final int MAX_DESCRIPTION_LENGTH = 120;
    private static final int LARGEST_PAYMENTS_LIMIT = 10;
    private static final int RECENT_TRANSACTIONS_LIMIT = 15;
    private static final int LOOKBACK_DAYS_FOR_LARGEST = 90;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SpendingCategoryRepository spendingCategoryRepository;

    public AssistantContextBuilder(AccountRepository accountRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    SpendingCategoryRepository spendingCategoryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.spendingCategoryRepository = spendingCategoryRepository;
    }

    public AssistantContext build(Long customerId) {
        Map<Long, String> categoryNames = spendingCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SpendingCategory::getId, SpendingCategory::getName));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        Instant thisMonthStartInstant = thisMonthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthStartInstant = thisMonthStart.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant lastMonthStartInstant = lastMonthStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Account> accounts = accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(customerId);
        List<AssistantContext.AccountFact> accountFacts = accounts.stream()
                .map(a -> new AssistantContext.AccountFact(
                        a.getAccountType().name(), a.getNickname(), a.getStatus().name(), money(a.getBalance())))
                .toList();

        BigDecimal spendThisMonth = ledgerEntryRepository.sumAllSpendingForCustomer(
                customerId, thisMonthStartInstant, nextMonthStartInstant);
        BigDecimal spendLastMonth = ledgerEntryRepository.sumAllSpendingForCustomer(
                customerId, lastMonthStartInstant, thisMonthStartInstant);

        List<CategorySpendingRow> categorySpend = ledgerEntryRepository.spendingByCategoryForCustomer(
                customerId, thisMonthStartInstant, nextMonthStartInstant);
        List<AssistantContext.CategoryAmountFact> categoryFacts = categorySpend.stream()
                .map(row -> new AssistantContext.CategoryAmountFact(
                        categoryNames.getOrDefault(row.categoryId(), "Uncategorized"), money(row.totalSpent())))
                .toList();

        Instant lookbackStart = today.minusDays(LOOKBACK_DAYS_FOR_LARGEST).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TransactionHistoryRow> largest = ledgerEntryRepository.largestSpendingForCustomer(
                customerId, lookbackStart, PageRequest.of(0, LARGEST_PAYMENTS_LIMIT));
        List<AssistantContext.TransactionFact> largestFacts = largest.stream()
                .map(row -> toFact(row, categoryNames))
                .toList();

        List<TransactionHistoryRow> recent = ledgerEntryRepository.recentForCustomer(
                customerId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT));
        List<AssistantContext.TransactionFact> recentFacts = recent.stream()
                .map(row -> toFact(row, categoryNames))
                .toList();

        return new AssistantContext(today.toString(), accountFacts, money(spendThisMonth), money(spendLastMonth),
                categoryFacts, largestFacts, recentFacts);
    }

    private AssistantContext.TransactionFact toFact(TransactionHistoryRow row, Map<Long, String> categoryNames) {
        String category = row.categoryId() == null ? "Uncategorized" : categoryNames.getOrDefault(row.categoryId(), "Uncategorized");
        return new AssistantContext.TransactionFact(
                row.reference(), money(row.amount()), category, truncate(row.description()), row.createdAt().toString());
    }

    private static String truncate(String description) {
        if (description == null) {
            return "";
        }
        String flattened = description.replaceAll("\\s+", " ").trim();
        return flattened.length() > MAX_DESCRIPTION_LENGTH ? flattened.substring(0, MAX_DESCRIPTION_LENGTH) : flattened;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.toPlainString();
    }
}
