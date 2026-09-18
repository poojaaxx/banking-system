CREATE TABLE spending_categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_spending_category_code UNIQUE (code)
) ENGINE=InnoDB;

INSERT INTO spending_categories (code, name, is_default) VALUES
  ('GROCERIES',    'Groceries',           TRUE),
  ('UTILITIES',    'Utilities',           TRUE),
  ('ENTERTAINMENT','Entertainment',       TRUE),
  ('DINING',       'Dining & Food',       TRUE),
  ('TRANSPORT',    'Transport',           TRUE),
  ('SHOPPING',     'Shopping',            TRUE),
  ('BILLS',        'Bills & Recharges',   TRUE),
  ('TRANSFERS',    'Transfers to others', TRUE),
  ('SAVINGS',      'Savings & Goals',     TRUE),
  ('OTHER',        'Other',               TRUE);
