package com.bankingdemo.billpay;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.SystemAccounts;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.idempotency.IdempotencyOperationType;
import com.bankingdemo.idempotency.IdempotencyService;
import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simulated fictional bill payments -- same reliable ledger + idempotency
 * machinery as any other movement. No real bill is ever paid; the UI must
 * make that explicit.
 */
@Service
@RequiredArgsConstructor
public class BillPayService {

    private final BillerRepository billerRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final BillPayExecutor billPayExecutor;

    private record BillPayFingerprint(Long customerId, Long accountId, Long billerId, BigDecimal amount, String note) {
    }

    @Transactional(readOnly = true)
    public List<Biller> activeBillers() {
        return billerRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Page<BillPayment> historyFor(Long customerId, Pageable pageable) {
        return billPaymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    public MoneyMovementReceipt pay(Long customerId, Long accountId, Long billerId, BigDecimal amount,
                                     String referenceNote, String idempotencyKey) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Amount must be positive");
        }
        Account account = ledgerService.requireOwnedActiveAccount(customerId, accountId);
        Biller biller = billerRepository.findByIdAndActiveTrue(billerId)
                .orElseThrow(() -> ApiException.badRequest("Unknown or inactive biller"));
        Account systemBillpay = ledgerService.requireSystemAccount(SystemAccounts.BILLPAY_ACCOUNT_NUMBER);

        return idempotencyService.execute(customerId, IdempotencyOperationType.BILL_PAYMENT, idempotencyKey,
                new BillPayFingerprint(customerId, accountId, billerId, amount, referenceNote), MoneyMovementReceipt.class,
                rowId -> billPayExecutor.payBill(rowId, customerId, account.getId(), systemBillpay.getId(),
                        biller.getId(), amount, biller.getCategoryId(), referenceNote));
    }
}
