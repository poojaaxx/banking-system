package com.bankingdemo.adminapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FreezeRequest(@NotBlank @Size(max = 255) String reason) {
}
