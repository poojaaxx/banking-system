package com.bankingdemo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String fullName,
        @NotBlank String email,
        @NotBlank String username,
        @NotBlank String password
) {
}
