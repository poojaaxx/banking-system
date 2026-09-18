package com.bankingdemo.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByFinancialTransactionId(Long financialTransactionId);

    @Query("""
            select new com.bankingdemo.ledger.TransactionHistoryRow(
                e.id, f.id, f.reference, f.type, e.direction, e.amount, e.balanceAfter,
                f.description, e.categoryId, e.createdAt, f.sourceAccountId, f.destinationAccountId)
            from LedgerEntry e, FinancialTransaction f
            where e.financialTransactionId = f.id
              and e.accountId = :accountId
              and (:type is null or f.type = :type)
              and (:fromDate is null or e.createdAt >= :fromDate)
              and (:toDate is null or e.createdAt <= :toDate)
              and (:minAmount is null or e.amount >= :minAmount)
              and (:maxAmount is null or e.amount <= :maxAmount)
              and (:search is null
                   or lower(f.description) like lower(concat('%', :search, '%'))
                   or lower(f.reference) like lower(concat('%', :search, '%')))
            order by e.createdAt desc, e.id desc
            """)
    Page<TransactionHistoryRow> searchHistory(
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select new com.bankingdemo.ledger.TransactionHistoryRow(
                e.id, f.id, f.reference, f.type, e.direction, e.amount, e.balanceAfter,
                f.description, e.categoryId, e.createdAt, f.sourceAccountId, f.destinationAccountId)
            from LedgerEntry e, FinancialTransaction f
            where e.financialTransactionId = f.id
              and e.accountId = :accountId
            order by e.createdAt desc, e.id desc
            """)
    List<TransactionHistoryRow> findAllHistoryForStatement(@Param("accountId") Long accountId, Pageable pageable);

    @Query("""
            select new com.bankingdemo.ledger.TransactionHistoryRow(
                e.id, f.id, f.reference, f.type, e.direction, e.amount, e.balanceAfter,
                f.description, e.categoryId, e.createdAt, f.sourceAccountId, f.destinationAccountId)
            from LedgerEntry e, FinancialTransaction f, Account own
            where e.financialTransactionId = f.id
              and e.accountId = own.id
              and own.ownerCustomerId = :customerId
            order by e.createdAt desc, e.id desc
            """)
    List<TransactionHistoryRow> recentForCustomer(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Spending is scoped to the CUSTOMER (across all their accounts), not one
     * account, since budgets are per-customer. Self-transfers (both legs
     * owned by the same customer) are excluded from spending -- see CLAUDE.md.
     */
    @Query("""
            select coalesce(sum(e.amount), 0)
            from LedgerEntry e, FinancialTransaction f, Account own, Account dst
            where e.financialTransactionId = f.id
              and e.accountId = own.id
              and f.destinationAccountId = dst.id
              and own.ownerCustomerId = :customerId
              and e.direction = 'DEBIT'
              and e.categoryId = :categoryId
              and e.createdAt >= :monthStart
              and e.createdAt < :monthEnd
              and (f.type <> 'TRANSFER' or own.ownerCustomerId <> dst.ownerCustomerId)
            """)
    BigDecimal sumSpendingByCategoryForCustomer(
            @Param("customerId") Long customerId,
            @Param("categoryId") Long categoryId,
            @Param("monthStart") Instant monthStart,
            @Param("monthEnd") Instant monthEnd);

    @Query("""
            select new com.bankingdemo.ledger.CategorySpendingRow(e.categoryId, sum(e.amount))
            from LedgerEntry e, FinancialTransaction f, Account own, Account dst
            where e.financialTransactionId = f.id
              and e.accountId = own.id
              and f.destinationAccountId = dst.id
              and own.ownerCustomerId = :customerId
              and e.direction = 'DEBIT'
              and e.categoryId is not null
              and e.createdAt >= :monthStart
              and e.createdAt < :monthEnd
              and (f.type <> 'TRANSFER' or own.ownerCustomerId <> dst.ownerCustomerId)
            group by e.categoryId
            """)
    List<CategorySpendingRow> spendingByCategoryForCustomer(
            @Param("customerId") Long customerId,
            @Param("monthStart") Instant monthStart,
            @Param("monthEnd") Instant monthEnd);
}
