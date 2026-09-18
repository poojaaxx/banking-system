CREATE TABLE beneficiaries (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id             BIGINT       NOT NULL,
    beneficiary_account_id  BIGINT       NOT NULL,
    nickname                VARCHAR(60)  NOT NULL,
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_beneficiary UNIQUE (customer_id, beneficiary_account_id),
    CONSTRAINT fk_ben_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_ben_account  FOREIGN KEY (beneficiary_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB;

-- Durable notification store. The auto-increment `id` doubles as the
-- monotonic per-recipient-stream event id used for SSE Last-Event-ID replay.
CREATE TABLE notifications (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_type       ENUM('CUSTOMER','ADMIN') NOT NULL,
    recipient_id         BIGINT       NOT NULL,
    type                 VARCHAR(60)  NOT NULL,
    title                VARCHAR(120) NOT NULL,
    body                 VARCHAR(500) NOT NULL,
    related_entity_type  VARCHAR(60)  NULL,
    related_entity_id    BIGINT       NULL,
    is_read              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_notif_recipient ON notifications(recipient_type, recipient_id, id);
CREATE INDEX idx_notif_unread ON notifications(recipient_type, recipient_id, is_read);

-- Single-use customer account-recovery codes. Only secure hashes are stored;
-- plaintext codes are shown exactly once, to the authenticated customer.
CREATE TABLE recovery_codes (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT       NOT NULL,
    code_hash        VARCHAR(100) NOT NULL,
    used_at          DATETIME(6)  NULL,
    invalidated_at   DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rc_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE INDEX idx_rc_customer_active ON recovery_codes(customer_id, used_at, invalidated_at);
