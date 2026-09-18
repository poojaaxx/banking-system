package com.bankingdemo.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AccountAlertRepository extends JpaRepository<AccountAlert, Long> {

    Page<AccountAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AccountAlert> findByAcknowledgedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    long countByAccountIdAndCreatedAtAfter(Long accountId, Instant after);

    long countByAcknowledgedAtIsNull();
}
