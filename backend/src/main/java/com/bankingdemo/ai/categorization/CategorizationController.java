package com.bankingdemo.ai.categorization;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/ledger-entries/{ledgerEntryId}")
public class CategorizationController {

    private final TransactionCategorizationService categorizationService;

    public CategorizationController(TransactionCategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    /** Always available (no external call) -- the default suggestion shown inline in the transaction list. */
    @GetMapping("/category-suggestion")
    public ResponseEntity<CategorySuggestion> ruleBasedSuggestion(@PathVariable Long ledgerEntryId) {
        Long customerId = SecurityUtils.requireCustomerId();
        CategorySuggestion suggestion = categorizationService.ruleBasedSuggestion(customerId, ledgerEntryId);
        return suggestion == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(suggestion);
    }

    /** On-demand, one call per click -- never invoked automatically/in bulk (Groq free-tier rate limits). */
    @PostMapping("/category-suggestion/ai")
    public CategorySuggestion aiSuggestion(@PathVariable Long ledgerEntryId) {
        Long customerId = SecurityUtils.requireCustomerId();
        CategorySuggestion suggestion = categorizationService.aiSuggestion(customerId, ledgerEntryId);
        if (suggestion == null) {
            throw ApiException.unprocessable("AI category suggestions are currently unavailable");
        }
        return suggestion;
    }

    @PatchMapping("/category")
    public ResponseEntity<Void> recategorize(@PathVariable Long ledgerEntryId, @Valid @RequestBody RecategorizeRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        categorizationService.recordChoice(customerId, ledgerEntryId, request.categoryId(), auditSourceFor(request.acceptedFrom()));
        return ResponseEntity.noContent().build();
    }

    private static String auditSourceFor(RecategorizeRequest.AcceptedSuggestionSource source) {
        if (source == null) {
            return "CUSTOMER_MANUAL";
        }
        return switch (source) {
            case MANUAL -> "CUSTOMER_MANUAL";
            case RULE_BASED -> "CUSTOMER_ACCEPTED_RULE";
            case AI -> "CUSTOMER_ACCEPTED_AI";
        };
    }
}
