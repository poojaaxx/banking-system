package com.bankingdemo.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByCustomerIdAndOperationTypeAndIdempotencyKey(
            Long customerId, IdempotencyOperationType operationType, String idempotencyKey);
}
