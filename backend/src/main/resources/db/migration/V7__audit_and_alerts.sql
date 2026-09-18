-- Audit trail for security-sensitive administrative actions (freeze/unfreeze,
-- support responses, alert acknowledgement). Admins never move money, so this
-- table never records balance-changing actions by an admin.
CREATE TABLE audit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id     BIGINT       NOT NULL,
    action       VARCHAR(80)  NOT NULL,
    target_type  VARCHAR(60)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    reason       VARCHAR(500) NULL,
    metadata     JSON         NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES admins(id)
) ENGINE=InnoDB;

CREATE INDEX idx_audit_target ON audit_log(target_type, target_id, created_at);
CREATE INDEX idx_audit_admin ON audit_log(admin_id, created_at);

-- Small, explainable, configurable rule set. These are simple threshold rules,
-- never described anywhere in the app as "AI fraud detection".
CREATE TABLE alert_rules (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(60)   NOT NULL,
    description       VARCHAR(255)  NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    threshold_amount  DECIMAL(19,2) NULL,
    threshold_count   INT           NULL,
    window_minutes    INT           NULL,
    CONSTRAINT uq_alert_rule_code UNIQUE (code)
) ENGINE=InnoDB;

INSERT INTO alert_rules (code, description, enabled, threshold_amount, threshold_count, window_minutes) VALUES
  ('LARGE_TRANSACTION',      'Single demo transaction exceeds the configured large-amount threshold', TRUE, 50000.00, NULL, NULL),
  ('REPEATED_FAILED_LOGIN',  'Repeated failed login attempts for the same customer in a short window', TRUE, NULL, 5, 15),
  ('RAPID_TRANSFERS',        'Many outgoing transfers from the same account in a short window',        TRUE, NULL, 5, 10);

CREATE TABLE account_alerts (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code                 VARCHAR(60)  NOT NULL,
    account_id                BIGINT       NULL,
    customer_id               BIGINT       NULL,
    financial_transaction_id  BIGINT       NULL,
    severity                  ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
    message                   VARCHAR(500) NOT NULL,
    created_at                DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_at           DATETIME(6)  NULL,
    acknowledged_by_admin_id  BIGINT       NULL,
    CONSTRAINT fk_aa_rule    FOREIGN KEY (rule_code) REFERENCES alert_rules(code),
    CONSTRAINT fk_aa_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_aa_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_aa_ft FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT fk_aa_admin FOREIGN KEY (acknowledged_by_admin_id) REFERENCES admins(id)
) ENGINE=InnoDB;

CREATE INDEX idx_aa_unacknowledged ON account_alerts(acknowledged_at, created_at);
CREATE INDEX idx_aa_customer ON account_alerts(customer_id);
