package com.bankingdemo.ai.categorization;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only trail of every category change on a ledger entry. The entry's
 * own {@code category_id} column remains the single mutable "current value"
 * (customers could already set it at transaction time); this table exists so
 * every subsequent change is traceable to who made it and why -- it is never
 * itself read to determine the current category.
 */
@Entity
@Table(name = "ledger_entry_category_audit")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntryCategoryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_entry_id", nullable = false)
    private Long ledgerEntryId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "previous_category_id")
    private Long previousCategoryId;

    @Column(name = "new_category_id", nullable = false)
    private Long newCategoryId;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntryCategoryAudit(Long ledgerEntryId, Long customerId, Long previousCategoryId,
                                     Long newCategoryId, String source) {
        this.ledgerEntryId = ledgerEntryId;
        this.customerId = customerId;
        this.previousCategoryId = previousCategoryId;
        this.newCategoryId = newCategoryId;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
