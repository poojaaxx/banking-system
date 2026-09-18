package com.bankingdemo.moneyrequest;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.ledger.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Runs the accept-a-money-request flow in one transaction: the guarded
 * PENDING -> ACCEPTED transition (so a request can never be accepted twice)
 * plus the normal idempotent transfer machinery, plus attaching the
 * resulting transaction id back onto the request. Kept on its own bean (like
 * LedgerEngine) so @Transactional applies correctly when invoked from
 * MoneyRequestService via IdempotencyService.
 */
@Component
@RequiredArgsConstructor
class MoneyRequestExecutor {

    private final MoneyRequestRepository moneyRequestRepository;
    private final LedgerService ledgerService;

    @Transactional
    public MoneyMovementReceipt acceptAndTransfer(Long idempotencyRowId, Long requestId, Long payerCustomerId,
                                                   Long payerAccountId, Long requesterAccountId,
                                                   BigDecimal amount, String description) {
        int updated = moneyRequestRepository.transitionFromPending(requestId, MoneyRequestStatus.ACCEPTED, Instant.now());
        if (updated != 1) {
            throw ApiException.conflict("This money request has already been responded to");
        }

        MoneyMovementReceipt receipt = ledgerService.moveForFeature(idempotencyRowId, payerAccountId, requesterAccountId,
                amount, TransactionType.TRANSFER, payerCustomerId, description, null);

        MoneyRequest request = moneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Money request disappeared mid-transaction"));
        request.setFinancialTransactionId(receipt.financialTransactionId());
        moneyRequestRepository.save(request);

        return receipt;
    }
}
