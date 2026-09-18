package com.bankingdemo.idempotency;

import com.bankingdemo.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

/**
 * Durable, database-backed idempotency for money-moving operations. See
 * CLAUDE.md and IdempotencyKeyStore for the full protocol: the claim step
 * commits in its own transaction (so the DB unique constraint -- not an
 * in-memory map -- is what makes concurrent duplicate requests safe), and the
 * business logic runs in a separate transaction that marks the row COMPLETED
 * on success or gets marked FAILED (safe to retry) if it throws.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyStore store;
    private final ObjectMapper objectMapper;

    /**
     * @param canonicalRequestPayload a value whose JSON form uniquely and deterministically
     *                                represents "what this request asked for"; used to reject
     *                                key reuse with a different payload.
     * @param businessLogic           receives the claimed idempotency row id (so it can mark
     *                                the row COMPLETED with a response snapshot inside its own
     *                                transaction) and returns the response to hand back to the
     *                                client -- and, on replay, to reconstruct from storage.
     */
    public <T> T execute(Long customerId, IdempotencyOperationType type, String idempotencyKey,
                          Object canonicalRequestPayload, Class<T> responseType,
                          Function<Long, T> businessLogic) {
        String fingerprint = RequestFingerprint.sha256Hex(canonicalRequestPayload, objectMapper);

        Long rowId;
        try {
            rowId = store.claimNew(customerId, type, idempotencyKey, fingerprint).getId();
        } catch (DataIntegrityViolationException raceLost) {
            IdempotencyKey existing = store.find(customerId, type, idempotencyKey)
                    .orElseThrow(() -> ApiException.conflict("Could not resolve idempotency key"));

            if (!existing.getRequestFingerprintHash().equals(fingerprint)) {
                throw ApiException.conflict("This idempotency key was already used for a different request");
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                return objectMapper.readValue(existing.getResponseSnapshot(), responseType);
            }
            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw ApiException.conflict("This operation is already being processed; please wait and refresh");
            }
            // FAILED: safe to retry, but only one concurrent caller may claim it.
            if (!store.claimFailedForRetry(existing.getId())) {
                throw ApiException.conflict("This operation is already being processed; please wait and refresh");
            }
            rowId = existing.getId();
        }

        try {
            return businessLogic.apply(rowId);
        } catch (RuntimeException ex) {
            store.markFailed(rowId);
            throw ex;
        }
    }
}
