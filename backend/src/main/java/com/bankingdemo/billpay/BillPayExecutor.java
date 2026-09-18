package com.bankingdemo.billpay;

import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.ledger.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
class BillPayExecutor {

    private final LedgerService ledgerService;
    private final BillPaymentRepository billPaymentRepository;

    @Transactional
    public MoneyMovementReceipt payBill(Long idempotencyRowId, Long customerId, Long accountId, Long systemBillpayAccountId,
                                         Long billerId, BigDecimal amount, Long categoryId, String referenceNote) {
        MoneyMovementReceipt receipt = ledgerService.moveForFeature(idempotencyRowId, accountId, systemBillpayAccountId,
                amount, TransactionType.BILL_PAYMENT, customerId, "Bill payment: " + referenceNote, categoryId);

        BillPayment payment = new BillPayment();
        payment.setFinancialTransactionId(receipt.financialTransactionId());
        payment.setCustomerId(customerId);
        payment.setBillerId(billerId);
        payment.setAccountId(accountId);
        payment.setAmount(amount);
        payment.setReferenceNote(referenceNote);
        billPaymentRepository.save(payment);

        return receipt;
    }
}
