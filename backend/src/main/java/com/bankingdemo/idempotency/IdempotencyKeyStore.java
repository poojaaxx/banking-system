package com.bankingdemo.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Isolated-transaction operations on idempotency_keys, kept on their own bean
 * so each runs in its own committed transaction (REQUIRES_NEW) regardless of
 * whether the caller is transactional -- this is what lets the claim step
 * commit and become visible to a concurrent duplicate request even while the
 * main money-moving transaction is still in flight.
 */
@Component
@RequiredArgsConstructor
class IdempotencyKeyStore {

    private final IdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey claimNew(Long customerId, IdempotencyOperationType type, String key, String fingerprintHash) {
        IdempotencyKey row = new IdempotencyKey();
        row.setCustomerId(customerId);
        row.setOperationType(type);
        row.setIdempotencyKey(key);
        row.setRequestFingerprintHash(fingerprintHash);
        row.setStatus(IdempotencyStatus.IN_PROGRESS);
        return repository.saveAndFlush(row);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyKey> find(Long customerId, IdempotencyOperationType type, String key) {
        return repository.findByCustomerIdAndOperationTypeAndIdempotencyKey(customerId, type, key);
    }

    /** @return true if this caller won the race to retry the previously-failed row. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimFailedForRetry(Long id) {
        return repository.claimFailedForRetry(id) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id) {
        repository.markFailed(id);
    }
}
