package com.bankingdemo.notification.sse;

import com.bankingdemo.notification.Notification;

import java.time.Instant;

public record NotificationEventDto(
        Long id,
        String type,
        String title,
        String body,
        String relatedEntityType,
        Long relatedEntityId,
        boolean read,
        Instant createdAt
) {
    public static NotificationEventDto from(Notification n) {
        return new NotificationEventDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getRelatedEntityType(), n.getRelatedEntityId(), n.isRead(), n.getCreatedAt());
    }
}
