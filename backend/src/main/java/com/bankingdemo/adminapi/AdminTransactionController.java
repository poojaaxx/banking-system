package com.bankingdemo.adminapi;

import com.bankingdemo.adminapi.dto.AdminLedgerEntrySummary;
import com.bankingdemo.adminapi.dto.AdminTransactionDetail;
import com.bankingdemo.adminapi.dto.AdminTransactionSummary;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<AdminTransactionSummary> list(@RequestParam(required = false) String reference,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        Page<FinancialTransaction> results = (reference == null || reference.isBlank())
                ? financialTransactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                : financialTransactionRepository.findByReferenceContainingIgnoreCaseOrderByCreatedAtDesc(reference.trim(), pageable);
        return results.map(AdminTransactionSummary::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminTransactionDetail get(@PathVariable Long id) {
        FinancialTransaction transaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        var entries = ledgerEntryRepository.findByFinancialTransactionId(id).stream()
                .map(AdminLedgerEntrySummary::from).toList();
        return new AdminTransactionDetail(AdminTransactionSummary.from(transaction), entries);
    }
}
