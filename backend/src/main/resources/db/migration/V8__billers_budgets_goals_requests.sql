-- Simulated fictional billers. Paying one of these is a normal ledger movement
-- against the SYSTEM_BILLPAY account; no real bill is ever paid.
CREATE TABLE billers (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    category_id  BIGINT       NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_biller_code UNIQUE (code),
    CONSTRAINT fk_biller_category FOREIGN KEY (category_id) REFERENCES spending_categories(id)
) ENGINE=InnoDB;

INSERT INTO billers (code, name, category_id, active)
SELECT 'ELECTRICITY_CO', 'Demo Electricity Board', id, TRUE FROM spending_categories WHERE code = 'UTILITIES';
INSERT INTO billers (code, name, category_id, active)
SELECT 'WATER_CO', 'Demo Water Utility', id, TRUE FROM spending_categories WHERE code = 'UTILITIES';
INSERT INTO billers (code, name, category_id, active)
SELECT 'INTERNET_CO', 'Demo Broadband Co', id, TRUE FROM spending_categories WHERE code = 'BILLS';
INSERT INTO billers (code, name, category_id, active)
SELECT 'MOBILE_CO', 'Demo Mobile Recharge', id, TRUE FROM spending_categories WHERE code = 'BILLS';

CREATE TABLE bill_payments (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    financial_transaction_id  BIGINT        NOT NULL,
    customer_id               BIGINT        NOT NULL,
    biller_id                 BIGINT        NOT NULL,
    account_id                BIGINT        NOT NULL,
    amount                    DECIMAL(19,2) NOT NULL,
    reference_note            VARCHAR(150)  NULL,
    created_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bp_ft       FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT fk_bp_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_bp_biller   FOREIGN KEY (biller_id) REFERENCES billers(id),
    CONSTRAINT fk_bp_account  FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB;

-- Monthly budgets per spending category, computed from real ledger data.
-- Transfers between the customer's own accounts are excluded from spending
-- totals at the query level (see SpendingService), not modeled here.
CREATE TABLE budgets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT        NOT NULL,
    category_id   BIGINT        NOT NULL,
    month_start   DATE          NOT NULL,
    limit_amount  DECIMAL(19,2) NOT NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_budget UNIQUE (customer_id, category_id, month_start),
    CONSTRAINT fk_budget_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES spending_categories(id),
    CONSTRAINT chk_budget_limit_positive CHECK (limit_amount > 0)
) ENGINE=InnoDB;

-- Each savings goal is backed by its own dedicated customer savings account,
-- so goal progress is simply that account's real stored balance -- never a
-- separately tracked number that could drift from the ledger.
CREATE TABLE savings_goals (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id        BIGINT        NOT NULL,
    name               VARCHAR(100)  NOT NULL,
    target_amount      DECIMAL(19,2) NOT NULL,
    target_date        DATE          NULL,
    linked_account_id  BIGINT        NOT NULL,
    status             ENUM('ACTIVE','COMPLETED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_goal_account UNIQUE (linked_account_id),
    CONSTRAINT fk_goal_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_goal_account  FOREIGN KEY (linked_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_goal_target_positive CHECK (target_amount > 0)
) ENGINE=InnoDB;

-- Peer-to-peer money requests. Acceptance creates exactly one TRANSFER
-- financial_transaction (via the normal idempotent transfer path) and the
-- status flip PENDING -> ACCEPTED is a conditional UPDATE guarded in the same
-- transaction, so a request can never be accepted twice.
CREATE TABLE money_requests (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_customer_id     BIGINT        NOT NULL,
    requester_account_id      BIGINT        NOT NULL,
    payer_customer_id         BIGINT        NOT NULL,
    amount                    DECIMAL(19,2) NOT NULL,
    note                      VARCHAR(255)  NULL,
    status                    ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    financial_transaction_id  BIGINT        NULL,
    created_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at              DATETIME(6)   NULL,
    CONSTRAINT fk_mr_requester FOREIGN KEY (requester_customer_id) REFERENCES customers(id),
    CONSTRAINT fk_mr_account   FOREIGN KEY (requester_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_mr_payer     FOREIGN KEY (payer_customer_id) REFERENCES customers(id),
    CONSTRAINT fk_mr_ft        FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT chk_mr_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_mr_payer ON money_requests(payer_customer_id, status);
CREATE INDEX idx_mr_requester ON money_requests(requester_customer_id, status);
