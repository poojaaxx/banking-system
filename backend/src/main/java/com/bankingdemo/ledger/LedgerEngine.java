package com.bankingdemo.ledger;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.AccountStatus;
import com.bankingdemo.alert.AlertEvaluationService;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.idempotency.IdempotencyKey;
import com.bankingdemo.idempotency.IdempotencyKeyRepository;
import com.bankingdemo.idempotency.IdempotencyStatus;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.RecipientType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The one place that actually moves fictional money. Every public method
 * here runs in a single transaction covering: pessimistic account locks
 * (acquired in ascending-id order to avoid deadlocks), state/balance
 * validation, the two balanced ledger rows, the account balance updates, the
 * idempotency-row completion, and any resulting notification -- all commit
 * together or all roll back together.
 */
@Component
@RequiredArgsConstructor
class LedgerEngine {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final NotificationService notificationService;
    private final ReferenceGenerator referenceGenerator;
    private final ObjectMapper objectMapper;
    private final AlertEvaluationService alertEvaluationService;

    @Transactional
    public MoneyMovementReceipt executeMovement(Long idempotencyRowId, MovementRequest request) {
        Long firstId = Math.min(request.sourceAccountId(), request.destinationAccountId());
        Long secondId = Math.max(request.sourceAccountId(), request.destinationAccountId());

        Account first = lockOrThrow(firstId);
        Account second = lockOrThrow(secondId);

        Account source = request.sourceAccountId().equals(first.getId()) ? first : second;
        Account destination = request.sourceAccountId().equals(first.getId()) ? second : first;

        validateOperable(source);
        validateOperable(destination);

        if (!source.isSystem() && source.getBalance().compareTo(request.amount()) < 0) {
            throw ApiException.badRequest("Insufficient funds");
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        source.setVersion(source.getVersion() + 1);
        destination.setBalance(destination.getBalance().add(request.amount()));
        destination.setVersion(destination.getVersion() + 1);
        accountRepository.save(source);
        accountRepository.save(destination);

        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setReference(referenceGenerator.generate());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setInitiatedByCustomerId(request.initiatedByCustomerId());
        transaction.setSourceAccountId(source.getId());
        transaction.setDestinationAccountId(destination.getId());
        transaction.setDescription(request.description());
        transaction = transactionRepository.saveAndFlush(transaction);

        LedgerEntry debit = new LedgerEntry();
        debit.setFinancialTransactionId(transaction.getId());
        debit.setAccountId(source.getId());
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmount(request.amount());
        debit.setBalanceAfter(source.getBalance());
        debit.setCategoryId(source.isSystem() ? null : request.debitCategoryId());
        ledgerEntryRepository.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setFinancialTransactionId(transaction.getId());
        credit.setAccountId(destination.getId());
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmount(request.amount());
        credit.setBalanceAfter(destination.getBalance());
        ledgerEntryRepository.save(credit);

        notifyRecipientIfExternalTransfer(request, destination);
        alertEvaluationService.evaluateTransaction(transaction);

        MoneyMovementReceipt receipt = buildReceipt(request, source, destination, transaction);

        IdempotencyKey idempotencyKey = idempotencyKeyRepository.findById(idempotencyRowId)
                .orElseThrow(() -> new IllegalStateException("Idempotency row disappeared mid-transaction"));
        idempotencyKey.setStatus(IdempotencyStatus.COMPLETED);
        idempotencyKey.setFinancialTransactionId(transaction.getId());
        idempotencyKey.setResponseSnapshot(objectMapper.writeValueAsString(receipt));
        idempotencyKey.setCompletedAt(Instant.now());
        idempotencyKeyRepository.save(idempotencyKey);

        return receipt;
    }

    private Account lockOrThrow(Long accountId) {
        return accountRepository.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    private void validateOperable(Account account) {
        if (account.isSystem()) {
            return;
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw ApiException.badRequest("Account " + account.getAccountNumber() + " is frozen");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.badRequest("Account " + account.getAccountNumber() + " is closed");
        }
    }

    private void notifyRecipientIfExternalTransfer(MovementRequest request, Account destination) {
        if (request.type() != TransactionType.TRANSFER) {
            return;
        }
        if (destination.getOwnerCustomerId() == null) {
            return;
        }
        if (destination.getOwnerCustomerId().equals(request.initiatedByCustomerId())) {
            return; // moving money between one's own accounts -- no "you received money" notification
        }
        notificationService.create(
                RecipientType.CUSTOMER,
                destination.getOwnerCustomerId(),
                "TRANSFER_RECEIVED",
                "Money received",
                "You received ₹" + request.amount() + " into account " + destination.getAccountNumber(),
                "ACCOUNT",
                destination.getId());
    }

    private MoneyMovementReceipt buildReceipt(MovementRequest request, Account source, Account destination, FinancialTransaction transaction) {
        BigDecimal sourceBalanceAfter = source.getOwnerCustomerId() != null
                && source.getOwnerCustomerId().equals(request.initiatedByCustomerId())
                ? source.getBalance() : null;
        BigDecimal destinationBalanceAfter = destination.getOwnerCustomerId() != null
                && destination.getOwnerCustomerId().equals(request.initiatedByCustomerId())
                ? destination.getBalance() : null;

        return new MoneyMovementReceipt(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                transaction.getAmount(),
                source.getAccountNumber(),
                destination.getAccountNumber(),
                sourceBalanceAfter,
                destinationBalanceAfter,
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
