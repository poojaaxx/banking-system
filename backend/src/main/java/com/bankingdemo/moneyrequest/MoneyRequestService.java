package com.bankingdemo.moneyrequest;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.idempotency.IdempotencyOperationType;
import com.bankingdemo.idempotency.IdempotencyService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.RecipientType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Peer-to-peer money requests. Acceptance always goes through the same
 * idempotent, ledger-backed transfer path as any other transfer -- it can
 * never execute more than once (see MoneyRequestExecutor), and rejection or
 * cancellation never move money at all.
 */
@Service
@RequiredArgsConstructor
public class MoneyRequestService {

    private final MoneyRequestRepository moneyRequestRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MoneyRequestExecutor moneyRequestExecutor;

    private record AcceptFingerprint(Long requestId, Long payerCustomerId) {
    }

    @Transactional
    public MoneyRequest create(Long requesterCustomerId, Long requesterAccountId, String payerAccountNumber,
                                BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Amount must be positive");
        }
        Account requesterAccount = accountRepository.findById(requesterAccountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!requesterCustomerId.equals(requesterAccount.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this account");
        }
        Account payerAccount = accountRepository.findByAccountNumber(payerAccountNumber)
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.badRequest("No account found with that account number"));
        if (payerAccount.getOwnerCustomerId().equals(requesterCustomerId)) {
            throw ApiException.badRequest("You cannot request money from yourself");
        }

        MoneyRequest request = new MoneyRequest();
        request.setRequesterCustomerId(requesterCustomerId);
        request.setRequesterAccountId(requesterAccountId);
        request.setPayerCustomerId(payerAccount.getOwnerCustomerId());
        request.setAmount(amount);
        request.setNote(note);
        request.setStatus(MoneyRequestStatus.PENDING);
        request = moneyRequestRepository.save(request);

        notificationService.create(RecipientType.CUSTOMER, payerAccount.getOwnerCustomerId(), "MONEY_REQUEST",
                "Money request received", "You were asked to send ₹" + amount,
                "MONEY_REQUEST", request.getId());
        return request;
    }

    @Transactional(readOnly = true)
    public Page<MoneyRequest> incomingForPayer(Long payerCustomerId, Pageable pageable) {
        return moneyRequestRepository.findByPayerCustomerIdOrderByCreatedAtDesc(payerCustomerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MoneyRequest> outgoingForRequester(Long requesterCustomerId, Pageable pageable) {
        return moneyRequestRepository.findByRequesterCustomerIdOrderByCreatedAtDesc(requesterCustomerId, pageable);
    }

    public MoneyMovementReceipt accept(Long payerCustomerId, Long requestId, Long payerAccountId, String idempotencyKey) {
        MoneyRequest request = moneyRequestRepository.findByIdAndPayerCustomerId(requestId, payerCustomerId)
                .orElseThrow(() -> ApiException.notFound("Money request not found"));
        if (request.getStatus() != MoneyRequestStatus.PENDING) {
            throw ApiException.badRequest("This request is no longer pending");
        }

        Account payerAccount = resolvePayerAccount(payerCustomerId, payerAccountId);

        return idempotencyService.execute(payerCustomerId, IdempotencyOperationType.REQUEST_MONEY_ACCEPT, idempotencyKey,
                new AcceptFingerprint(requestId, payerCustomerId), MoneyMovementReceipt.class,
                rowId -> moneyRequestExecutor.acceptAndTransfer(rowId, requestId, payerCustomerId,
                        payerAccount.getId(), request.getRequesterAccountId(), request.getAmount(),
                        "Money request: " + (request.getNote() != null ? request.getNote() : "")));
    }

    private Account resolvePayerAccount(Long payerCustomerId, Long payerAccountId) {
        if (payerAccountId != null) {
            Account account = accountRepository.findById(payerAccountId)
                    .orElseThrow(() -> ApiException.notFound("Account not found"));
            if (!payerCustomerId.equals(account.getOwnerCustomerId())) {
                throw ApiException.forbidden("You do not own this account");
            }
            return account;
        }
        return accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(payerCustomerId).stream()
                .findFirst().orElseThrow(() -> ApiException.badRequest("You have no account to pay from"));
    }

    @Transactional
    public void reject(Long payerCustomerId, Long requestId) {
        MoneyRequest request = moneyRequestRepository.findByIdAndPayerCustomerId(requestId, payerCustomerId)
                .orElseThrow(() -> ApiException.notFound("Money request not found"));
        int updated = moneyRequestRepository.transitionFromPending(request.getId(), MoneyRequestStatus.REJECTED, Instant.now());
        if (updated != 1) {
            throw ApiException.badRequest("This request is no longer pending");
        }
        notificationService.create(RecipientType.CUSTOMER, request.getRequesterCustomerId(), "MONEY_REQUEST_REJECTED",
                "Money request declined", "Your request for ₹" + request.getAmount() + " was declined",
                "MONEY_REQUEST", request.getId());
    }

    @Transactional
    public void cancel(Long requesterCustomerId, Long requestId) {
        MoneyRequest request = moneyRequestRepository.findByIdAndRequesterCustomerId(requestId, requesterCustomerId)
                .orElseThrow(() -> ApiException.notFound("Money request not found"));
        int updated = moneyRequestRepository.transitionFromPending(request.getId(), MoneyRequestStatus.CANCELLED, Instant.now());
        if (updated != 1) {
            throw ApiException.badRequest("This request is no longer pending");
        }
    }
}
