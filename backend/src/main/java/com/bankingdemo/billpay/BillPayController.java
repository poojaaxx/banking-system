package com.bankingdemo.billpay;

import com.bankingdemo.billpay.dto.BillPaymentResponse;
import com.bankingdemo.billpay.dto.BillerResponse;
import com.bankingdemo.billpay.dto.PayBillRequest;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/bills")
@RequiredArgsConstructor
public class BillPayController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final BillPayService billPayService;

    @GetMapping("/billers")
    public List<BillerResponse> billers() {
        return billPayService.activeBillers().stream().map(BillerResponse::from).toList();
    }

    @GetMapping("/payments")
    public Page<BillPaymentResponse> history(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Long customerId = SecurityUtils.requireCustomerId();
        return billPayService.historyFor(customerId, PageRequest.of(page, Math.min(size, 100))).map(BillPaymentResponse::from);
    }

    @PostMapping("/pay")
    public MoneyMovementReceipt pay(@Valid @RequestBody PayBillRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.badRequest("A valid Idempotency-Key header is required");
        }
        Long customerId = SecurityUtils.requireCustomerId();
        return billPayService.pay(customerId, request.accountId(), request.billerId(), request.amount(),
                request.referenceNote(), idempotencyKey);
    }
}
