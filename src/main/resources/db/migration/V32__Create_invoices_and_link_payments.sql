-- Migration: Create invoices table and link payment attempts to invoices
-- Purpose: Support a unified invoice-driven payment flow across M-Pesa and card

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_token VARCHAR(64) NOT NULL UNIQUE,
    invoice_number VARCHAR(64) NOT NULL UNIQUE,
    payer_user_id UUID NOT NULL,
    amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'EXPIRED', 'VOID', 'FAILED')),
    description VARCHAR(500),
    metadata TEXT,
    redirect_success_url TEXT,
    redirect_cancel_url TEXT,
    expires_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoices_public_token ON invoices(public_token);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_payer_user_id ON invoices(payer_user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON invoices(created_at);

-- Link payments to invoices (nullable for backward compatibility with existing flows)
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS invoice_id UUID;

ALTER TABLE payments
DROP CONSTRAINT IF EXISTS fk_payments_invoice_id;

ALTER TABLE payments
ADD CONSTRAINT fk_payments_invoice_id
FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payments(invoice_id);

-- Extend payment type constraint with INVOICE
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_type_check;

ALTER TABLE payments ADD CONSTRAINT payments_payment_type_check
    CHECK (payment_type IN ('SESSION_BOOKING', 'SUBSCRIPTION', 'UPGRADE', 'ADDON', 'TOP_UP', 'REFUND', 'INVOICE'));

COMMENT ON TABLE invoices IS 'Invoice records used by the unified payment page';
COMMENT ON COLUMN invoices.public_token IS 'Public, non-guessable token used by unauthenticated payment page';
COMMENT ON COLUMN payments.invoice_id IS 'Optional link from payment attempt to invoice';
COMMENT ON COLUMN payments.payment_type IS 'Payment type: SESSION_BOOKING, SUBSCRIPTION, UPGRADE, ADDON, TOP_UP, REFUND, INVOICE';
