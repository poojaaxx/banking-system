package com.bankingdemo.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientTypeAndRecipientIdOrderByIdDesc(
            RecipientType recipientType, Long recipientId, Pageable pageable);

    long countByRecipientTypeAndRecipientIdAndReadFalse(RecipientType recipientType, Long recipientId);

    List<Notification> findByRecipientTypeAndRecipientIdAndIdGreaterThanOrderByIdAsc(
            RecipientType recipientType, Long recipientId, Long afterId);

    @Modifying
    @Query("update Notification n set n.read = true where n.id = :id and n.recipientType = :recipientType and n.recipientId = :recipientId")
    int markRead(@Param("id") Long id, @Param("recipientType") RecipientType recipientType, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipientType = :recipientType and n.recipientId = :recipientId and n.read = false")
    int markAllRead(@Param("recipientType") RecipientType recipientType, @Param("recipientId") Long recipientId);
}
