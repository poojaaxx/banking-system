package com.bankingdemo.notification.dto;

import com.bankingdemo.notification.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id, String type, String title, String body,
        String relatedEntityType, Long relatedEntityId, boolean read, Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getRelatedEntityType(), n.getRelatedEntityId(), n.isRead(), n.getCreatedAt());
    }
}
