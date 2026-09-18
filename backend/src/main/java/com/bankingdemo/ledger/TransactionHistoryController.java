package com.bankingdemo.ledger;

import com.bankingdemo.ledger.dto.TransactionHistoryResponse;
import com.bankingdemo.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/customer/accounts/{accountId}")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private static final int MAX_STATEMENT_ROWS = 5000;

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/transactions")
    public Page<TransactionHistoryResponse> search(
            @PathVariable Long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long customerId = SecurityUtils.requireCustomerId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return transactionHistoryService.search(customerId, accountId, type, from, to, minAmount, maxAmount, search, pageable);
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    public void statementCsv(@PathVariable Long accountId, HttpServletResponse response) throws IOException {
        Long customerId = SecurityUtils.requireCustomerId();
        List<TransactionHistoryResponse> rows = transactionHistoryService.forStatement(customerId, accountId, MAX_STATEMENT_ROWS);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"statement-" + accountId + ".csv\"");
        response.setHeader("Cache-Control", "no-store");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("Date,Reference,Type,Direction,Amount,BalanceAfter,Counterparty,Description");
            for (TransactionHistoryResponse row : rows) {
                writer.println(String.join(",",
                        CsvUtil.sanitizeCell(row.createdAt().toString()),
                        CsvUtil.sanitizeCell(row.reference()),
                        CsvUtil.sanitizeCell(row.type().name()),
                        CsvUtil.sanitizeCell(row.direction().name()),
                        CsvUtil.sanitizeCell(row.amount().toPlainString()),
                        CsvUtil.sanitizeCell(row.balanceAfter().toPlainString()),
                        CsvUtil.sanitizeCell(row.counterpartyDisplayName() != null ? row.counterpartyDisplayName() : ""),
                        CsvUtil.sanitizeCell(row.description() != null ? row.description() : "")));
            }
        }
    }
}
