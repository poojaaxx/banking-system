-- Customer-specific unusual-activity checks. These are statistical/threshold
-- rules over a customer's own spending history -- never described anywhere as
-- "AI fraud detection", and they only ever raise alerts/notifications (they
-- never freeze, block, or move anything). See docs/insights.md.

-- dedupe_key makes alert creation idempotent per (rule, key): the detector uses
-- INSERT IGNORE so a duplicate can never fail the money movement that triggered it.
-- Existing rows keep NULL (MySQL unique indexes allow multiple NULLs).
ALTER TABLE account_alerts ADD COLUMN dedupe_key VARCHAR(160) NULL;
CREATE UNIQUE INDEX uq_aa_rule_dedupe ON account_alerts(rule_code, dedupe_key);
CREATE INDEX idx_aa_customer_rule_created ON account_alerts(customer_id, rule_code, created_at);

-- UNUSUAL_LARGE_SPEND: threshold_amount = absolute floor (INR) below which a payment is never flagged,
--   threshold_count = minimum number of prior payments required before a baseline is trusted,
--   window_minutes  = look-back for that baseline (129600 min = 90 days).
-- REPEATED_PAYMENT: threshold_count = identical payments (incl. the current one) that trigger,
--   window_minutes = rolling window (1440 min = 24 h).
INSERT INTO alert_rules (code, description, enabled, threshold_amount, threshold_count, window_minutes) VALUES
  ('UNUSUAL_LARGE_SPEND', 'Payment far above the customer''s own recent spending pattern (Tukey far-out fence over prior payments)', TRUE, 1000.00, 5, 129600),
  ('REPEATED_PAYMENT',    'Repeated identical payments to the same recipient within a short window',                              TRUE, NULL,    3, 1440);
