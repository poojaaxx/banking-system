package com.bankingdemo.support.dto;

import com.bankingdemo.support.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRespondRequest(
        @NotBlank @Size(max = 2000) String message,
        TicketStatus newStatus
) {
}
