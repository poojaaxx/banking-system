package com.bankingdemo.ai.categorization;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryCategoryAuditRepository extends JpaRepository<LedgerEntryCategoryAudit, Long> {
}
