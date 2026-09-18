package com.bankingdemo.moneyrequest;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.moneyrequest.dto.AcceptMoneyRequestRequest;
import com.bankingdemo.moneyrequest.dto.CreateMoneyRequestRequest;
import com.bankingdemo.moneyrequest.dto.MoneyRequestResponse;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/money-requests")
@RequiredArgsConstructor
public class MoneyRequestController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final MoneyRequestService moneyRequestService;

    @GetMapping("/incoming")
    public Page<MoneyRequestResponse> incoming(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Long customerId = SecurityUtils.requireCustomerId();
        return moneyRequestService.incomingForPayer(customerId, PageRequest.of(page, Math.min(size, 100)))
                .map(MoneyRequestResponse::from);
    }

    @GetMapping("/outgoing")
    public Page<MoneyRequestResponse> outgoing(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Long customerId = SecurityUtils.requireCustomerId();
        return moneyRequestService.outgoingForRequester(customerId, PageRequest.of(page, Math.min(size, 100)))
                .map(MoneyRequestResponse::from);
    }

    @PostMapping
    public ResponseEntity<MoneyRequestResponse> create(@Valid @RequestBody CreateMoneyRequestRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        MoneyRequest created = moneyRequestService.create(customerId, request.requesterAccountId(),
                request.payerAccountNumber(), request.amount(), request.note());
        return ResponseEntity.ok(MoneyRequestResponse.from(created));
    }

    @PostMapping("/{id}/accept")
    public MoneyMovementReceipt accept(@PathVariable Long id, @RequestBody(required = false) AcceptMoneyRequestRequest request,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.badRequest("A valid Idempotency-Key header is required");
        }
        Long customerId = SecurityUtils.requireCustomerId();
        Long payerAccountId = request != null ? request.payerAccountId() : null;
        return moneyRequestService.accept(customerId, id, payerAccountId, idempotencyKey);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        moneyRequestService.reject(customerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        moneyRequestService.cancel(customerId, id);
        return ResponseEntity.noContent().build();
    }
}
