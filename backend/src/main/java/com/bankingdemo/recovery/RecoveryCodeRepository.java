package com.bankingdemo.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    @Query("select r from RecoveryCode r where r.customerId = :customerId and r.usedAt is null and r.invalidatedAt is null")
    List<RecoveryCode> findActiveByCustomerId(@Param("customerId") Long customerId);

    @Modifying
    @Query("update RecoveryCode r set r.usedAt = :now where r.id = :id and r.usedAt is null and r.invalidatedAt is null")
    int consume(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update RecoveryCode r set r.invalidatedAt = :now where r.customerId = :customerId and r.usedAt is null and r.invalidatedAt is null")
    int invalidateAllActive(@Param("customerId") Long customerId, @Param("now") Instant now);
}
