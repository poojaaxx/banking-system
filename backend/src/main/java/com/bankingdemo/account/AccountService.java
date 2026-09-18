package com.bankingdemo.account;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_ACCOUNTS_PER_CUSTOMER = 10;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    @Transactional(readOnly = true)
    public List<Account> listForCustomer(Long customerId) {
        return accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(customerId);
    }

    @Transactional(readOnly = true)
    public Account getOwned(Long customerId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!customerId.equals(account.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this account");
        }
        return account;
    }

    /** New customer accounts always start at zero -- never silently funded. */
    @Transactional
    public Account create(Long customerId, String nickname) {
        long existing = accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(customerId).size();
        if (existing >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw ApiException.badRequest("You have reached the maximum number of accounts for this demo");
        }

        Account account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setOwnerCustomerId(customerId);
        account.setAccountType(AccountType.SAVINGS);
        account.setNickname(nickname);
        account.setCurrency("INR");
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    /** Customers may only close their own eligible, zero-balance, active accounts. */
    @Transactional
    public void close(Long customerId, Long accountId) {
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        if (!customerId.equals(account.getOwnerCustomerId())) {
            throw ApiException.forbidden("You do not own this account");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.badRequest("Account is already closed");
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw ApiException.badRequest("A frozen account cannot be closed; contact support");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw ApiException.badRequest("Only a zero-balance account can be closed");
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(java.time.Instant.now());
        accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public RecipientLookupResult lookupRecipient(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.notFound("No account found with that account number"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.badRequest("This account is closed");
        }
        Customer owner = customerRepository.findById(account.getOwnerCustomerId())
                .orElseThrow(() -> new IllegalStateException("Account owner missing for account " + account.getId()));
        return new RecipientLookupResult(account.getAccountNumber(), owner.getFullName(), account.getNickname());
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = accountNumberGenerator.generate();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number after several attempts");
    }
}
