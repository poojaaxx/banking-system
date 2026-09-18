package com.bankingdemo.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 150) String subject,
        Long relatedTransactionId,
        @NotBlank @Size(max = 2000) String message
) {
}
