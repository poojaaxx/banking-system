-- Durable, database-backed idempotency for every money-moving operation.
-- The unique constraint is what makes concurrent duplicate submissions safe:
-- the second concurrent INSERT with the same (customer, operation, key) fails
-- fast with a constraint violation instead of racing to create two transfers.
CREATE TABLE idempotency_keys (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id                 BIGINT        NOT NULL,
    operation_type              ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','BILL_PAYMENT','REQUEST_MONEY_ACCEPT') NOT NULL,
    idempotency_key             VARCHAR(80)   NOT NULL,
    request_fingerprint_hash    CHAR(64)      NOT NULL,
    status                      ENUM('IN_PROGRESS','COMPLETED','FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
    financial_transaction_id    BIGINT        NULL,
    response_snapshot           JSON          NULL,
    created_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at                DATETIME(6)   NULL,
    CONSTRAINT uq_idem_scope UNIQUE (customer_id, operation_type, idempotency_key),
    CONSTRAINT fk_idem_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_idem_ft FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id)
) ENGINE=InnoDB;

CREATE INDEX idx_idem_ft ON idempotency_keys(financial_transaction_id);
