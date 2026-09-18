package com.bankingdemo.notification.sse;

import com.bankingdemo.notification.CurrentRecipient;
import com.bankingdemo.notification.Notification;
import com.bankingdemo.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticated (via the existing session cookie -- never a token in the URL)
 * SSE stream, scoped to whichever recipient (customer or admin) is currently
 * logged in. On reconnect the browser's EventSource automatically resends the
 * id of the last event it saw via Last-Event-ID, which we use to replay any
 * notifications persisted while the connection was down before switching to
 * live delivery -- so a missed event is never silently lost.
 */
@RestController
@RequiredArgsConstructor
public class EventStreamController {

    private final SseEventPublisher sseEventPublisher;
    private final NotificationRepository notificationRepository;

    @GetMapping(value = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        CurrentRecipient recipient = CurrentRecipient.resolve();
        SseEmitter emitter = sseEventPublisher.subscribe(recipient.type(), recipient.id());

        try {
            if (lastEventId != null) {
                List<Notification> backlog = notificationRepository
                        .findByRecipientTypeAndRecipientIdAndIdGreaterThanOrderByIdAsc(recipient.type(), recipient.id(), lastEventId);
                for (Notification n : backlog) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(n.getId()))
                            .name("notification")
                            .data(NotificationEventDto.from(n)));
                }
            }
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
