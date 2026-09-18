package com.bankingdemo.moneyrequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, Long> {

    Optional<MoneyRequest> findByIdAndPayerCustomerId(Long id, Long payerCustomerId);

    Optional<MoneyRequest> findByIdAndRequesterCustomerId(Long id, Long requesterCustomerId);

    Page<MoneyRequest> findByPayerCustomerIdOrderByCreatedAtDesc(Long payerCustomerId, Pageable pageable);

    Page<MoneyRequest> findByRequesterCustomerIdOrderByCreatedAtDesc(Long requesterCustomerId, Pageable pageable);

    /**
     * Guarded transition: only succeeds (returns 1) if the request was still
     * PENDING, so a request can never be accepted/rejected/cancelled twice
     * even under concurrent duplicate submissions.
     */
    @Modifying
    @Query("update MoneyRequest r set r.status = :newStatus, r.respondedAt = :now where r.id = :id and r.status = 'PENDING'")
    int transitionFromPending(@Param("id") Long id, @Param("newStatus") MoneyRequestStatus newStatus, @Param("now") Instant now);
}
