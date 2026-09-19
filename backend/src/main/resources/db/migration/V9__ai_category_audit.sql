-- Append-only audit trail for customer-driven recategorization (manual pick,
-- or accepting a rule-based/AI suggestion). ledger_entries.category_id remains
-- the single mutable "current category" column (it was already customer-settable
-- at transaction time via debitCategoryId) -- this table exists purely so every
-- change to it is traceable, not as a second source of truth.
CREATE TABLE ledger_entry_category_audit (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_entry_id       BIGINT NOT NULL,
    customer_id           BIGINT NOT NULL,
    previous_category_id  BIGINT NULL,
    new_category_id       BIGINT NOT NULL,
    source                VARCHAR(30) NOT NULL,
    created_at            TIMESTAMP NOT NULL,
    CONSTRAINT fk_leca_entry FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entries(id),
    CONSTRAINT fk_leca_prev_category FOREIGN KEY (previous_category_id) REFERENCES spending_categories(id),
    CONSTRAINT fk_leca_new_category FOREIGN KEY (new_category_id) REFERENCES spending_categories(id)
) ENGINE=InnoDB;

CREATE INDEX idx_leca_entry ON ledger_entry_category_audit(ledger_entry_id);
