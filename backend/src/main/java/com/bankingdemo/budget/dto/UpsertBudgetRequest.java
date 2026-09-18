package com.bankingdemo.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertBudgetRequest(
        @NotNull Long categoryId,
        @NotNull LocalDate monthStart,
        @NotNull @DecimalMin(value = "0.01") BigDecimal limitAmount
) {
}
