package com.bankingdemo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RecoveryLoginRequest(
        @NotBlank String username,
        @NotBlank String code
) {
}
