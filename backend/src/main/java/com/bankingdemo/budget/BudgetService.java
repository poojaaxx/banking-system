package com.bankingdemo.budget;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.CategorySpendingRow;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.SpendingCategory;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final SpendingCategoryRepository spendingCategoryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public Budget upsert(Long customerId, Long categoryId, LocalDate monthStart, BigDecimal limitAmount) {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Budget limit must be positive");
        }
        spendingCategoryRepository.findById(categoryId).orElseThrow(() -> ApiException.badRequest("Unknown category"));
        LocalDate normalizedMonth = monthStart.withDayOfMonth(1);

        Budget budget = budgetRepository.findByCustomerIdAndCategoryIdAndMonthStart(customerId, categoryId, normalizedMonth)
                .orElseGet(Budget::new);
        budget.setCustomerId(customerId);
        budget.setCategoryId(categoryId);
        budget.setMonthStart(normalizedMonth);
        budget.setLimitAmount(limitAmount);
        return budgetRepository.save(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetProgress> progressForMonth(Long customerId, LocalDate monthStart) {
        LocalDate normalizedMonth = monthStart.withDayOfMonth(1);
        var monthStartInstant = normalizedMonth.atStartOfDay(ZoneOffset.UTC).toInstant();
        var monthEndInstant = normalizedMonth.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<Long, BigDecimal> limitsByCategory = new HashMap<>();
        for (Budget b : budgetRepository.findByCustomerIdAndMonthStart(customerId, normalizedMonth)) {
            limitsByCategory.put(b.getCategoryId(), b.getLimitAmount());
        }

        Map<Long, BigDecimal> spentByCategory = new HashMap<>();
        for (CategorySpendingRow row : ledgerEntryRepository.spendingByCategoryForCustomer(customerId, monthStartInstant, monthEndInstant)) {
            spentByCategory.put(row.categoryId(), row.totalSpent());
        }

        List<SpendingCategory> categories = spendingCategoryRepository.findAll();
        return categories.stream()
                .filter(c -> limitsByCategory.containsKey(c.getId()) || spentByCategory.containsKey(c.getId()))
                .map(c -> new BudgetProgress(c.getId(), c.getName(),
                        limitsByCategory.get(c.getId()),
                        spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpendingCategory> categories() {
        return spendingCategoryRepository.findAll();
    }
}
