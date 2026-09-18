package com.bankingdemo.notification;

import com.bankingdemo.notification.sse.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEventPublisher sseEventPublisher;

    /**
     * Persists the notification as part of the caller's current transaction,
     * and only pushes it over SSE after that transaction actually commits --
     * a rolled-back financial operation never produces a phantom live update.
     */
    public Notification create(RecipientType recipientType, Long recipientId, String type,
                                String title, String body, String relatedEntityType, Long relatedEntityId) {
        Notification notification = new Notification();
        notification.setRecipientType(recipientType);
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        Notification saved = notificationRepository.save(notification);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseEventPublisher.publish(saved);
                }
            });
        } else {
            sseEventPublisher.publish(saved);
        }
        return saved;
    }
}
