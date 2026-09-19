package com.bankingdemo.testsupport;

import com.bankingdemo.notification.Notification;
import com.bankingdemo.notification.RecipientType;
import com.bankingdemo.notification.sse.SseEventPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records what would be pushed over SSE (after commit), then delegates to the real publisher. */
public class RecordingSseEventPublisher extends SseEventPublisher {

    public record Published(RecipientType recipientType, Long recipientId, String type, String title) {
    }

    private final List<Published> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Notification notification) {
        published.add(new Published(notification.getRecipientType(), notification.getRecipientId(),
                notification.getType(), notification.getTitle()));
        super.publish(notification);
    }

    public List<Published> publishedTo(Long customerId) {
        return published.stream()
                .filter(p -> p.recipientType() == RecipientType.CUSTOMER && p.recipientId().equals(customerId))
                .toList();
    }

    public void clear() {
        published.clear();
    }
}
