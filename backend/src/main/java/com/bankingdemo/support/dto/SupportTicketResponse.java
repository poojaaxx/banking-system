package com.bankingdemo.support.dto;

import com.bankingdemo.support.SupportTicket;

import java.time.Instant;

public record SupportTicketResponse(
        Long id, Long customerId, String subject, String status, Long relatedTransactionId,
        Instant createdAt, Instant updatedAt
) {
    public static SupportTicketResponse from(SupportTicket t) {
        return new SupportTicketResponse(t.getId(), t.getCustomerId(), t.getSubject(), t.getStatus().name(),
                t.getRelatedTransactionId(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
