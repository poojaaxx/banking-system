package com.bankingdemo.savingsgoal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContributeRequest(
        @NotNull Long sourceAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
