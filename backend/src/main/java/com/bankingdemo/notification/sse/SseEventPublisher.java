package com.bankingdemo.notification.sse;

import com.bankingdemo.notification.Notification;
import com.bankingdemo.notification.RecipientType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-process SSE fan-out. This is the live-update *hint* channel only -- the
 * database (notifications table + everything it points at) is always the
 * source of truth; a client that misses an event, or never connects at all,
 * gets a correct picture by refetching. Bounded per recipient and by a
 * generous but finite emitter timeout so a flood of tabs/reconnects can't
 * grow server resource use unboundedly.
 */
@Component
public class SseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);
    private static final long TIMEOUT_MILLIS = 15 * 60 * 1000L; // 15 minutes; client EventSource auto-reconnects
    private static final int MAX_EMITTERS_PER_RECIPIENT = 5;
    private static final int MAX_TOTAL_EMITTERS = 5000;

    private final Map<String, ConcurrentLinkedDeque<SseEmitter>> byRecipient = new ConcurrentHashMap<>();

    public SseEmitter subscribe(RecipientType type, Long recipientId) {
        if (totalEmitterCount() >= MAX_TOTAL_EMITTERS) {
            throw new IllegalStateException("Too many active live-update connections; try again shortly");
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        String key = key(type, recipientId);
        ConcurrentLinkedDeque<SseEmitter> deque = byRecipient.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(emitter);
        while (deque.size() > MAX_EMITTERS_PER_RECIPIENT) {
            SseEmitter oldest = deque.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }

        emitter.onCompletion(() -> deque.remove(emitter));
        emitter.onTimeout(() -> {
            deque.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> deque.remove(emitter));
        return emitter;
    }

    public void publish(Notification notification) {
        String key = key(notification.getRecipientType(), notification.getRecipientId());
        ConcurrentLinkedDeque<SseEmitter> deque = byRecipient.get(key);
        if (deque == null || deque.isEmpty()) {
            return;
        }
        NotificationEventDto dto = NotificationEventDto.from(notification);
        for (SseEmitter emitter : List.copyOf(deque)) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(dto));
            } catch (IOException | IllegalStateException e) {
                deque.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }

    /** Called on logout / session invalidation so a signed-out browser stops receiving live updates. */
    public void closeAll(RecipientType type, Long recipientId) {
        ConcurrentLinkedDeque<SseEmitter> deque = byRecipient.remove(key(type, recipientId));
        if (deque != null) {
            deque.forEach(SseEmitter::complete);
        }
    }

    private int totalEmitterCount() {
        return byRecipient.values().stream().mapToInt(ConcurrentLinkedDeque::size).sum();
    }

    private static String key(RecipientType type, Long recipientId) {
        return type.name() + ":" + recipientId;
    }
}
