-- Customers (public/registered users of the demo bank)
CREATE TABLE customers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(120)  NOT NULL,
    email           VARCHAR(190)  NOT NULL,
    username        VARCHAR(60)   NOT NULL,
    password_hash   VARCHAR(100)  NOT NULL,
    status          ENUM('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_customers_email    UNIQUE (email),
    CONSTRAINT uq_customers_username UNIQUE (username)
) ENGINE=InnoDB;

-- Administrators (no public registration; bootstrapped from env vars at startup)
CREATE TABLE admins (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(60)   NOT NULL,
    email           VARCHAR(190)  NOT NULL,
    password_hash   VARCHAR(100)  NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_admins_username UNIQUE (username),
    CONSTRAINT uq_admins_email    UNIQUE (email)
) ENGINE=InnoDB;

-- Accounts: both ordinary customer (SAVINGS) accounts and internal SYSTEM counterpart
-- accounts used only as the "other side" of deposits/withdrawals/bill payments.
-- SYSTEM accounts are never owned by a customer, never appear in recipient search,
-- and are excluded from customer balance totals.
CREATE TABLE accounts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number      CHAR(12)      NOT NULL,
    owner_customer_id   BIGINT        NULL,
    account_type        ENUM('SAVINGS','SYSTEM') NOT NULL DEFAULT 'SAVINGS',
    nickname            VARCHAR(60)   NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'INR',
    status              ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    balance             DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version             BIGINT        NOT NULL DEFAULT 0,
    frozen_reason       VARCHAR(255)  NULL,
    closed_at           DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (owner_customer_id) REFERENCES customers(id),
    CONSTRAINT chk_accounts_balance_nonnegative CHECK (account_type = 'SYSTEM' OR balance >= 0),
    CONSTRAINT chk_accounts_ownership CHECK (
        (account_type = 'SYSTEM'  AND owner_customer_id IS NULL) OR
        (account_type = 'SAVINGS' AND owner_customer_id IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE INDEX idx_accounts_owner ON accounts(owner_customer_id);
CREATE INDEX idx_accounts_type_status ON accounts(account_type, status);

-- Seed the two internal system counterpart accounts used by the ledger engine.
-- Account numbers in the 9000000000xx range are reserved for system accounts
-- and are never issued to customers (see AccountNumberGenerator).
INSERT INTO accounts (account_number, owner_customer_id, account_type, nickname, currency, status, balance)
VALUES
  ('900000000001', NULL, 'SYSTEM', 'SYSTEM_CASH',    'INR', 'ACTIVE', 0.00),
  ('900000000002', NULL, 'SYSTEM', 'SYSTEM_BILLPAY', 'INR', 'ACTIVE', 0.00);
