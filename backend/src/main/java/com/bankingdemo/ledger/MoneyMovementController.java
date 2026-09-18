package com.bankingdemo.ledger;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.ledger.dto.DepositRequest;
import com.bankingdemo.ledger.dto.TransferRequest;
import com.bankingdemo.ledger.dto.WithdrawRequest;
import com.bankingdemo.security.RateLimiter;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Every endpoint here requires an Idempotency-Key header from the client.
 * The frontend must generate one key per attempted operation and keep
 * resubmitting the SAME key until it learns the outcome -- see
 * IdempotencyService for what happens on replay/conflict.
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class MoneyMovementController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final LedgerService ledgerService;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;

    @PostMapping("/deposits")
    public MoneyMovementReceipt deposit(@Valid @RequestBody DepositRequest request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long customerId = SecurityUtils.requireCustomerId();
        enforceFundingRateLimit(customerId);
        return ledgerService.deposit(customerId, request.accountId(), request.amount(),
                request.description(), requireValidKey(idempotencyKey));
    }

    @PostMapping("/withdrawals")
    public MoneyMovementReceipt withdraw(@Valid @RequestBody WithdrawRequest request,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long customerId = SecurityUtils.requireCustomerId();
        enforceFundingRateLimit(customerId);
        return ledgerService.withdraw(customerId, request.accountId(), request.amount(),
                request.description(), requireValidKey(idempotencyKey));
    }

    @PostMapping("/transfers")
    public MoneyMovementReceipt transfer(@Valid @RequestBody TransferRequest request,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long customerId = SecurityUtils.requireCustomerId();
        return ledgerService.transfer(customerId, request.sourceAccountId(), request.destinationAccountNumber(),
                request.amount(), request.description(), request.categoryId(), requireValidKey(idempotencyKey));
    }

    private void enforceFundingRateLimit(Long customerId) {
        if (!rateLimiter.tryConsume("funding:" + customerId, appProperties.getRateLimit().getFundingPerMinute(), Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("Too many funding operations. Please wait and try again.");
        }
    }

    private String requireValidKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.badRequest("A valid Idempotency-Key header is required");
        }
        return idempotencyKey;
    }
}
