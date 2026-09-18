package com.bankingdemo.billpay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayBillRequest(
        @NotNull Long accountId,
        @NotNull Long billerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 150) String referenceNote
) {
}
