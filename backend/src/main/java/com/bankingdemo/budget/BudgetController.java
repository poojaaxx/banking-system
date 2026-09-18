package com.bankingdemo.budget;

import com.bankingdemo.budget.dto.SpendingCategoryResponse;
import com.bankingdemo.budget.dto.UpsertBudgetRequest;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/customer/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/categories")
    public List<SpendingCategoryResponse> categories() {
        return budgetService.categories().stream().map(SpendingCategoryResponse::from).toList();
    }

    @GetMapping
    public List<BudgetProgress> progress(@RequestParam(required = false) LocalDate month) {
        Long customerId = SecurityUtils.requireCustomerId();
        return budgetService.progressForMonth(customerId, month != null ? month : LocalDate.now());
    }

    @PostMapping
    public ResponseEntity<Void> upsert(@Valid @RequestBody UpsertBudgetRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        budgetService.upsert(customerId, request.categoryId(), request.monthStart(), request.limitAmount());
        return ResponseEntity.noContent().build();
    }
}
