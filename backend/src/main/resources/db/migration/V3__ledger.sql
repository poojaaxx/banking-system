-- Append-only balanced ledger. Every completed movement of fictional money is
-- one financial_transactions row plus exactly two ledger_entries rows
-- (one DEBIT, one CREDIT) whose amounts match. Nothing here is ever
-- UPDATEd or DELETEd by application code; corrections are new linked rows.
CREATE TABLE financial_transactions (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference                   VARCHAR(40)   NOT NULL,
    type                        ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','BILL_PAYMENT') NOT NULL,
    amount                      DECIMAL(19,2) NOT NULL,
    initiated_by_customer_id    BIGINT        NOT NULL,
    source_account_id           BIGINT        NOT NULL,
    destination_account_id      BIGINT        NOT NULL,
    description                 VARCHAR(255)  NULL,
    created_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_financial_transactions_reference UNIQUE (reference),
    CONSTRAINT fk_ft_initiator FOREIGN KEY (initiated_by_customer_id) REFERENCES customers(id),
    CONSTRAINT fk_ft_source      FOREIGN KEY (source_account_id)      REFERENCES accounts(id),
    CONSTRAINT fk_ft_destination FOREIGN KEY (destination_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_ft_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ft_distinct_accounts CHECK (source_account_id <> destination_account_id)
) ENGINE=InnoDB;

CREATE INDEX idx_ft_initiator  ON financial_transactions(initiated_by_customer_id, created_at);
CREATE INDEX idx_ft_source     ON financial_transactions(source_account_id, created_at);
CREATE INDEX idx_ft_dest       ON financial_transactions(destination_account_id, created_at);

CREATE TABLE ledger_entries (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    financial_transaction_id    BIGINT        NOT NULL,
    account_id                  BIGINT        NOT NULL,
    direction                   ENUM('DEBIT','CREDIT') NOT NULL,
    amount                      DECIMAL(19,2) NOT NULL,
    balance_after               DECIMAL(19,2) NOT NULL,
    category_id                 BIGINT        NULL,
    created_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_le_ft       FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT fk_le_account  FOREIGN KEY (account_id)   REFERENCES accounts(id),
    CONSTRAINT fk_le_category FOREIGN KEY (category_id)  REFERENCES spending_categories(id),
    CONSTRAINT uq_le_ft_direction UNIQUE (financial_transaction_id, direction),
    CONSTRAINT chk_le_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_le_account_created ON ledger_entries(account_id, created_at);
CREATE INDEX idx_le_category ON ledger_entries(category_id);
