package com.bankingdemo.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByOwnerCustomerIdOrderByCreatedAtAsc(Long ownerCustomerId);

    /**
     * Pessimistic row lock, acquired one account at a time. Callers that need to
     * lock two accounts (e.g. a transfer) MUST always lock them in ascending id
     * order to avoid deadlocks -- see LedgerService.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.accountType = 'SAVINGS' and a.status <> 'CLOSED'")
    BigDecimal sumAllCustomerBalances();

    long countByAccountType(AccountType accountType);

    Page<Account> findByAccountTypeOrderByCreatedAtDesc(AccountType accountType, Pageable pageable);

    Page<Account> findByAccountTypeAndStatusOrderByCreatedAtDesc(AccountType accountType, AccountStatus status, Pageable pageable);
}
