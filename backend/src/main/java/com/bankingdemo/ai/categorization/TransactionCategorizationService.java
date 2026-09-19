package com.bankingdemo.ai.categorization;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.LedgerEntry;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.SpendingCategory;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransactionCategorizationService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountRepository accountRepository;
    private final SpendingCategoryRepository spendingCategoryRepository;
    private final LedgerEntryCategoryAuditRepository auditRepository;
    private final RuleBasedCategorizer ruleBasedCategorizer;
    private final AiCategorizer aiCategorizer;

    public TransactionCategorizationService(LedgerEntryRepository ledgerEntryRepository,
                                             FinancialTransactionRepository financialTransactionRepository,
                                             AccountRepository accountRepository,
                                             SpendingCategoryRepository spendingCategoryRepository,
                                             LedgerEntryCategoryAuditRepository auditRepository,
                                             RuleBasedCategorizer ruleBasedCategorizer,
                                             AiCategorizer aiCategorizer) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.accountRepository = accountRepository;
        this.spendingCategoryRepository = spendingCategoryRepository;
        this.auditRepository = auditRepository;
        this.ruleBasedCategorizer = ruleBasedCategorizer;
        this.aiCategorizer = aiCategorizer;
    }

    @Transactional(readOnly = true)
    public CategorySuggestion ruleBasedSuggestion(Long customerId, Long ledgerEntryId) {
        OwnedEntry owned = loadOwned(customerId, ledgerEntryId);
        return ruleBasedCategorizer.suggestCode(owned.transaction().getDescription())
                .map(match -> toSuggestion(match.categoryCode(), CategorySuggestionSource.RULE_BASED, match.confidence()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CategorySuggestion aiSuggestion(Long customerId, Long ledgerEntryId) {
        OwnedEntry owned = loadOwned(customerId, ledgerEntryId);
        Set<String> allowedCodes = spendingCategoryRepository.findAll().stream()
                .map(SpendingCategory::getCode).collect(Collectors.toSet());
        return aiCategorizer.suggestCode(owned.transaction().getDescription(), owned.entry().getAmount(), allowedCodes)
                .map(match -> toSuggestion(match.categoryCode(), CategorySuggestionSource.AI, match.confidence()))
                .orElse(null);
    }

    @Transactional
    public void recordChoice(Long customerId, Long ledgerEntryId, Long categoryId, String auditSource) {
        OwnedEntry owned = loadOwned(customerId, ledgerEntryId);
        spendingCategoryRepository.findById(categoryId).orElseThrow(() -> ApiException.badRequest("Unknown category"));
        Long previous = owned.entry().getCategoryId();
        owned.entry().setCategoryId(categoryId);
        ledgerEntryRepository.save(owned.entry());
        auditRepository.save(new LedgerEntryCategoryAudit(ledgerEntryId, customerId, previous, categoryId, auditSource));
    }

    private OwnedEntry loadOwned(Long customerId, Long ledgerEntryId) {
        LedgerEntry entry = ledgerEntryRepository.findById(ledgerEntryId)
                .orElseThrow(() -> ApiException.notFound("Transaction entry not found"));
        Account account = accountRepository.findById(entry.getAccountId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!customerId.equals(account.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this transaction");
        }
        FinancialTransaction transaction = financialTransactionRepository.findById(entry.getFinancialTransactionId())
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        return new OwnedEntry(entry, transaction);
    }

    private CategorySuggestion toSuggestion(String code, CategorySuggestionSource source, double confidence) {
        SpendingCategory category = spendingCategoryRepository.findByCode(code)
                .or(() -> spendingCategoryRepository.findByCode("OTHER"))
                .orElseThrow(() -> new IllegalStateException("OTHER category missing"));
        // Confidence is intentionally not exposed: the rule score is a constant and a model's self-reported
        // confidence is uncalibrated, so neither may be presented to customers as a probability.
        return new CategorySuggestion(category.getId(), category.getCode(), category.getName(), source, null);
    }

    private record OwnedEntry(LedgerEntry entry, FinancialTransaction transaction) {
    }
}
