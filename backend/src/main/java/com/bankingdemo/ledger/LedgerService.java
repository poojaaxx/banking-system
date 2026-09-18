package com.bankingdemo.ledger;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.SystemAccounts;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.idempotency.IdempotencyOperationType;
import com.bankingdemo.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Public entry point for every money-moving customer action. Pre-checks
 * (amount shape, demo limits, ownership, destination resolution) happen here,
 * outside the idempotency/locking machinery; the actual movement is
 * delegated to {@link LedgerEngine} via {@link IdempotencyService}.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEngine ledgerEngine;
    private final IdempotencyService idempotencyService;
    private final AppProperties appProperties;

    private record DepositFingerprint(Long customerId, Long accountId, BigDecimal amount, String description) {
    }

    private record WithdrawalFingerprint(Long customerId, Long accountId, BigDecimal amount, String description) {
    }

    private record TransferFingerprint(Long customerId, Long sourceAccountId, String destinationAccountNumber,
                                        BigDecimal amount, String description) {
    }

    public MoneyMovementReceipt deposit(Long customerId, Long accountId, BigDecimal amount, String description, String idempotencyKey) {
        validateAmountShape(amount);
        validateNotExceeding(amount, appProperties.getDemoLimits().getMaxFundingAmount(), "funding");
        Account destination = requireOwnedActiveAccount(customerId, accountId);
        Account systemCash = requireSystemAccount(SystemAccounts.CASH_ACCOUNT_NUMBER);

        return idempotencyService.execute(customerId, IdempotencyOperationType.DEPOSIT, idempotencyKey,
                new DepositFingerprint(customerId, accountId, amount, description), MoneyMovementReceipt.class,
                rowId -> ledgerEngine.executeMovement(rowId, new MovementRequest(
                        systemCash.getId(), destination.getId(), amount, TransactionType.DEPOSIT,
                        customerId, description, null)));
    }

    public MoneyMovementReceipt withdraw(Long customerId, Long accountId, BigDecimal amount, String description, String idempotencyKey) {
        validateAmountShape(amount);
        validateNotExceeding(amount, appProperties.getDemoLimits().getMaxFundingAmount(), "withdrawal");
        Account source = requireOwnedActiveAccount(customerId, accountId);
        Account systemCash = requireSystemAccount(SystemAccounts.CASH_ACCOUNT_NUMBER);

        return idempotencyService.execute(customerId, IdempotencyOperationType.WITHDRAWAL, idempotencyKey,
                new WithdrawalFingerprint(customerId, accountId, amount, description), MoneyMovementReceipt.class,
                rowId -> ledgerEngine.executeMovement(rowId, new MovementRequest(
                        source.getId(), systemCash.getId(), amount, TransactionType.WITHDRAWAL,
                        customerId, description, null)));
    }

    public MoneyMovementReceipt transfer(Long customerId, Long sourceAccountId, String destinationAccountNumber,
                                          BigDecimal amount, String description, Long debitCategoryId, String idempotencyKey) {
        validateAmountShape(amount);
        validateNotExceeding(amount, appProperties.getDemoLimits().getMaxTransferAmount(), "transfer");
        Account source = requireOwnedActiveAccount(customerId, sourceAccountId);
        Account destination = accountRepository.findByAccountNumber(destinationAccountNumber)
                .orElseThrow(() -> ApiException.badRequest("No account found with that account number"));
        if (destination.isSystem()) {
            throw ApiException.badRequest("No account found with that account number");
        }
        if (destination.getId().equals(source.getId())) {
            throw ApiException.badRequest("Cannot transfer an account to itself");
        }

        return idempotencyService.execute(customerId, IdempotencyOperationType.TRANSFER, idempotencyKey,
                new TransferFingerprint(customerId, sourceAccountId, destinationAccountNumber, amount, description),
                MoneyMovementReceipt.class,
                rowId -> ledgerEngine.executeMovement(rowId, new MovementRequest(
                        source.getId(), destination.getId(), amount, TransactionType.TRANSFER,
                        customerId, description, debitCategoryId)));
    }

    /** Used by BillPayService and MoneyRequestService, which own their own idempotency scoping. */
    public MoneyMovementReceipt moveForFeature(Long idempotencyRowId, Long sourceAccountId, Long destinationAccountId,
                                                BigDecimal amount, TransactionType type, Long initiatedByCustomerId,
                                                String description, Long debitCategoryId) {
        return ledgerEngine.executeMovement(idempotencyRowId, new MovementRequest(
                sourceAccountId, destinationAccountId, amount, type, initiatedByCustomerId, description, debitCategoryId));
    }

    public Account requireOwnedActiveAccount(Long customerId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!customerId.equals(account.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this account");
        }
        return account;
    }

    public Account requireSystemAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalStateException("System account " + accountNumber + " is missing"));
    }

    private void validateAmountShape(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Amount must have at most 2 decimal places");
        }
    }

    private void validateNotExceeding(BigDecimal amount, BigDecimal limit, String operationName) {
        if (amount.compareTo(limit) > 0) {
            throw ApiException.badRequest("This demo limits each " + operationName + " to ₹" + limit);
        }
    }
}
