package com.bankingdemo.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountAlertRepository extends JpaRepository<AccountAlert, Long> {

    Page<AccountAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AccountAlert> findByAcknowledgedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    long countByAccountIdAndCreatedAtAfter(Long accountId, Instant after);

    long countByAcknowledgedAtIsNull();

    Optional<AccountAlert> findByRuleCodeAndDedupeKey(String ruleCode, String dedupeKey);

    boolean existsByRuleCodeAndCustomerIdAndDedupeKeyStartingWithAndCreatedAtAfter(
            String ruleCode, Long customerId, String dedupeKeyPrefix, Instant after);

    /** Always scoped by customer id: a customer only ever reads their own alerts. */
    List<AccountAlert> findByCustomerIdAndRuleCodeInOrderByCreatedAtDesc(
            Long customerId, Collection<String> ruleCodes, Pageable pageable);

    /**
     * INSERT IGNORE so a duplicate (same rule + dedupe key) is silently skipped instead of throwing --
     * an alert must never be able to fail, or roll back, the money movement that triggered it.
     * @return 1 if a row was inserted, 0 if it already existed.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO account_alerts
                (rule_code, account_id, customer_id, financial_transaction_id, severity, message, created_at, dedupe_key)
            VALUES (:ruleCode, :accountId, :customerId, :transactionId, :severity, :message, :createdAt, :dedupeKey)
            """, nativeQuery = true)
    int insertIgnore(@Param("ruleCode") String ruleCode,
                     @Param("accountId") Long accountId,
                     @Param("customerId") Long customerId,
                     @Param("transactionId") Long transactionId,
                     @Param("severity") String severity,
                     @Param("message") String message,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("dedupeKey") String dedupeKey);
}
