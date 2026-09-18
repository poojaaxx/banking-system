package com.bankingdemo.account;

import com.bankingdemo.audit.AuditService;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.RecipientType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Admin read/inspect + freeze/unfreeze only. Admins can never deposit,
 * withdraw, transfer, impersonate a customer, or directly change a balance --
 * there is deliberately no such method here.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Customer (SAVINGS) accounts only -- system accounts are never a target of admin inspection. */
    @Transactional(readOnly = true)
    public Page<Account> search(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return accountRepository.findByAccountTypeOrderByCreatedAtDesc(AccountType.SAVINGS, pageable);
        }
        AccountStatus parsed;
        try {
            parsed = AccountStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid status filter");
        }
        return accountRepository.findByAccountTypeAndStatusOrderByCreatedAtDesc(AccountType.SAVINGS, parsed, pageable);
    }

    @Transactional(readOnly = true)
    public Account getAny(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    @Transactional
    public Account freeze(Long adminId, Long accountId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("A reason is required to freeze an account");
        }
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (account.isSystem()) {
            throw ApiException.badRequest("System accounts cannot be frozen");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.badRequest("A closed account cannot be frozen");
        }
        account.setStatus(AccountStatus.FROZEN);
        account.setFrozenReason(reason);
        accountRepository.save(account);

        auditService.record(adminId, "FREEZE_ACCOUNT", "ACCOUNT", account.getId(), reason);
        notificationService.create(RecipientType.CUSTOMER, account.getOwnerCustomerId(), "ACCOUNT_FROZEN",
                "Account frozen", "Your account " + account.getAccountNumber() + " has been frozen: " + reason,
                "ACCOUNT", account.getId());
        return account;
    }

    @Transactional
    public Account unfreeze(Long adminId, Long accountId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("A reason is required to unfreeze an account");
        }
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw ApiException.badRequest("Account is not frozen");
        }
        account.setStatus(AccountStatus.ACTIVE);
        account.setFrozenReason(null);
        accountRepository.save(account);

        auditService.record(adminId, "UNFREEZE_ACCOUNT", "ACCOUNT", account.getId(), reason);
        notificationService.create(RecipientType.CUSTOMER, account.getOwnerCustomerId(), "ACCOUNT_UNFROZEN",
                "Account unfrozen", "Your account " + account.getAccountNumber() + " has been unfrozen: " + reason,
                "ACCOUNT", account.getId());
        return account;
    }
}
