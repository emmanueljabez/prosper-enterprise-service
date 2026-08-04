-- Migration: Add recurring billing/card-on-file fields to subscriptions
-- Purpose: Support CyberSource automatic subscription renewals

ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS auto_renew_card_on_file BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN IF NOT EXISTS auto_renew_payment_token VARCHAR(255),
ADD COLUMN IF NOT EXISTS auto_renew_customer_token VARCHAR(255),
ADD COLUMN IF NOT EXISTS auto_renew_payment_instrument_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS auto_renew_card_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS auto_renew_card_last_four VARCHAR(4),
ADD COLUMN IF NOT EXISTS auto_renew_tokenized_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS auto_renew_last_charge_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS auto_renew_last_failure_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_subscriptions_auto_renew_lookup
ON subscriptions(status, auto_renew, end_date, auto_renew_card_on_file);

COMMENT ON COLUMN subscriptions.auto_renew_card_on_file IS 'Whether a reusable CyberSource payment credential is stored';
COMMENT ON COLUMN subscriptions.auto_renew_payment_token IS 'CyberSource payment token (if returned by gateway)';
COMMENT ON COLUMN subscriptions.auto_renew_customer_token IS 'CyberSource customer token for recurring charges';
COMMENT ON COLUMN subscriptions.auto_renew_payment_instrument_id IS 'CyberSource payment instrument id for recurring charges';
COMMENT ON COLUMN subscriptions.auto_renew_tokenized_at IS 'Timestamp when card-on-file token data was captured';
COMMENT ON COLUMN subscriptions.auto_renew_last_charge_at IS 'Timestamp of last successful automatic renewal charge';
COMMENT ON COLUMN subscriptions.auto_renew_last_failure_reason IS 'Last automatic renewal failure reason';
