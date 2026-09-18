package com.bankingdemo.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(
        @NotBlank String accountNumber,
        @NotBlank @Size(max = 60) String nickname
) {
}
