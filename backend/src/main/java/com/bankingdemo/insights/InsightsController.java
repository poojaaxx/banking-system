package com.bankingdemo.insights;

import com.bankingdemo.alert.AccountAlert;
import com.bankingdemo.alert.AlertService;
import com.bankingdemo.alert.UnusualActivityDetector;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only. Every query is keyed by the authenticated customer's id, never by a client-supplied id. */
@RestController
@RequestMapping("/api/customer/insights")
public class InsightsController {

    private final InsightsService insightsService;
    private final AlertService alertService;
    private final FinancialTransactionRepository financialTransactionRepository;

    public InsightsController(InsightsService insightsService, AlertService alertService,
                              FinancialTransactionRepository financialTransactionRepository) {
        this.insightsService = insightsService;
        this.alertService = alertService;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @GetMapping
    public InsightsResponse insights() {
        return insightsService.compute(SecurityUtils.requireCustomerId());
    }

    /** The customer's own unusual-activity flags. Labeled as statistical checks / rules -- not fraud findings. */
    @GetMapping("/alerts")
    public List<UnusualActivityResponse> unusualActivity(@RequestParam(defaultValue = "20") int limit) {
        Long customerId = SecurityUtils.requireCustomerId();
        return alertService.listUnusualActivityForCustomer(customerId, limit).stream().map(this::toResponse).toList();
    }

    private UnusualActivityResponse toResponse(AccountAlert alert) {
        String reference = alert.getFinancialTransactionId() == null ? null
                : financialTransactionRepository.findById(alert.getFinancialTransactionId())
                .map(t -> t.getReference()).orElse(null);
        boolean large = UnusualActivityDetector.LARGE_SPEND_RULE.equals(alert.getRuleCode());
        return new UnusualActivityResponse(alert.getId(), alert.getRuleCode(),
                large ? "Unusually large payment (statistical check)" : "Repeated payments (rule)",
                alert.getMessage(), reference, alert.getCreatedAt().toString());
    }

    public record UnusualActivityResponse(Long id, String ruleCode, String label, String explanation,
                                          String transactionReference, String createdAt) {
    }
}
