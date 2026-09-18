package com.bankingdemo.notification;

import com.bankingdemo.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Shared between customer and admin frontends -- always scoped to whoever is currently authenticated. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        CurrentRecipient recipient = CurrentRecipient.resolve();
        return notificationRepository
                .findByRecipientTypeAndRecipientIdOrderByIdDesc(recipient.type(), recipient.id(), PageRequest.of(page, Math.min(size, 100)))
                .map(NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public long unreadCount() {
        CurrentRecipient recipient = CurrentRecipient.resolve();
        return notificationRepository.countByRecipientTypeAndRecipientIdAndReadFalse(recipient.type(), recipient.id());
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        CurrentRecipient recipient = CurrentRecipient.resolve();
        notificationRepository.markRead(id, recipient.type(), recipient.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead() {
        CurrentRecipient recipient = CurrentRecipient.resolve();
        notificationRepository.markAllRead(recipient.type(), recipient.id());
        return ResponseEntity.noContent().build();
    }
}
