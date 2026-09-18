package com.bankingdemo.support.dto;

import com.bankingdemo.support.SupportMessage;

import java.time.Instant;

public record SupportMessageResponse(Long id, String senderType, Long senderId, String body, Instant createdAt) {
    public static SupportMessageResponse from(SupportMessage m) {
        return new SupportMessageResponse(m.getId(), m.getSenderType().name(), m.getSenderId(), m.getBody(), m.getCreatedAt());
    }
}
