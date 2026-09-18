package com.bankingdemo.ledger;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerRepository;
import com.bankingdemo.ledger.dto.TransactionHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> search(Long customerId, Long accountId, TransactionType type,
                                                     Instant fromDate, Instant toDate, BigDecimal minAmount,
                                                     BigDecimal maxAmount, String search, Pageable pageable) {
        requireOwnedAccount(customerId, accountId);
        Page<TransactionHistoryRow> rows = ledgerEntryRepository.searchHistory(
                accountId, type, fromDate, toDate, minAmount, maxAmount, blankToNull(search), pageable);
        return rows.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> forStatement(Long customerId, Long accountId, int maxRows) {
        requireOwnedAccount(customerId, accountId);
        List<TransactionHistoryRow> rows = ledgerEntryRepository.findAllHistoryForStatement(
                accountId, org.springframework.data.domain.PageRequest.of(0, maxRows));
        return rows.stream().map(this::toResponse).toList();
    }

    private void requireOwnedAccount(Long customerId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!customerId.equals(account.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this account");
        }
    }

    private TransactionHistoryResponse toResponse(TransactionHistoryRow row) {
        Long counterpartyAccountId = row.direction() == LedgerDirection.DEBIT
                ? row.destinationAccountId() : row.sourceAccountId();
        CounterpartyInfo counterparty = resolveCounterparty(counterpartyAccountId);
        return new TransactionHistoryResponse(row.ledgerEntryId(), row.reference(), row.type(), row.direction(),
                row.amount(), row.balanceAfter(), row.description(), row.categoryId(), row.createdAt(),
                counterparty.accountNumber(), counterparty.displayName());
    }

    private record CounterpartyInfo(String accountNumber, String displayName) {
    }

    private CounterpartyInfo resolveCounterparty(Long accountId) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return new CounterpartyInfo(null, null);
        }
        if (account.isSystem()) {
            return new CounterpartyInfo(null, "Demo Bank");
        }
        String name = customerRepository.findById(account.getOwnerCustomerId())
                .map(Customer::getFullName)
                .orElse(null);
        return new CounterpartyInfo(account.getAccountNumber(), name);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
