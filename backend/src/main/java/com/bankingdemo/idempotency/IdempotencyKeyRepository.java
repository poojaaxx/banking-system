package com.bankingdemo.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByCustomerIdAndOperationTypeAndIdempotencyKey(
            Long customerId, IdempotencyOperationType operationType, String idempotencyKey);

    /** Atomic guard: only one concurrent retry can move a FAILED row back to IN_PROGRESS. */
    @Modifying
    @Query("update IdempotencyKey k set k.status = 'IN_PROGRESS' where k.id = :id and k.status = 'FAILED'")
    int claimFailedForRetry(@Param("id") Long id);

    @Modifying
    @Query("update IdempotencyKey k set k.status = 'FAILED' where k.id = :id and k.status = 'IN_PROGRESS'")
    int markFailed(@Param("id") Long id);
}
