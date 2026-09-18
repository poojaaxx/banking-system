package com.bankingdemo.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMessageRequest(@NotBlank @Size(max = 2000) String message) {
}
