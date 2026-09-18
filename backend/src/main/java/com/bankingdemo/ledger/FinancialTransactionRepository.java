package com.bankingdemo.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    Optional<FinancialTransaction> findByReference(String reference);

    Page<FinancialTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<FinancialTransaction> findByReferenceContainingIgnoreCaseOrderByCreatedAtDesc(String reference, Pageable pageable);
}
