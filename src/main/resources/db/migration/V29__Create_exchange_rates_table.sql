-- Create exchange_rates table for multi-currency support
CREATE TABLE IF NOT EXISTS exchange_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_code VARCHAR(3) NOT NULL UNIQUE,
    currency_name VARCHAR(100) NOT NULL,
    rate NUMERIC(19, 6) NOT NULL CHECK (rate > 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    symbol VARCHAR(10),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(50) DEFAULT 'MANUAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on currency_code for fast lookups
CREATE INDEX idx_exchange_rates_currency_code ON exchange_rates(currency_code);

-- Create index on is_active for filtering active currencies
CREATE INDEX idx_exchange_rates_is_active ON exchange_rates(is_active);

-- Create index on is_default for finding the default currency
CREATE INDEX idx_exchange_rates_is_default ON exchange_rates(is_default);

-- Insert default exchange rates for East African currencies
-- Rates are relative to USD (1 USD = X local currency)
INSERT INTO exchange_rates (currency_code, currency_name, rate, is_active, is_default, symbol, source)
VALUES
    ('KES', 'Kenyan Shilling', 130.00, true, true, 'KSh', 'MANUAL'),
    ('UGX', 'Ugandan Shilling', 3700.00, true, false, 'UGX', 'MANUAL'),
    ('TZS', 'Tanzanian Shilling', 2350.00, true, false, 'TSh', 'MANUAL'),
    ('RWF', 'Rwandan Franc', 1050.00, true, false, 'RWF', 'MANUAL'),
    ('NGN', 'Nigerian Naira', 750.00, true, false, '₦', 'MANUAL'),
    ('ZAR', 'South African Rand', 18.50, true, false, 'R', 'MANUAL'),
    ('GHS', 'Ghanaian Cedi', 12.00, true, false, 'GH₵', 'MANUAL')
ON CONFLICT (currency_code) DO NOTHING;

-- Add comment to table
COMMENT ON TABLE exchange_rates IS 'Exchange rates for multi-currency support. Rates are relative to USD (base currency).';

-- Add comments to important columns
COMMENT ON COLUMN exchange_rates.rate IS 'Exchange rate from USD to this currency (e.g., 1 USD = 130 KES means rate = 130.0)';
COMMENT ON COLUMN exchange_rates.is_default IS 'Whether this is the default currency for the platform (typically KES)';
COMMENT ON COLUMN exchange_rates.source IS 'Source of the rate: MANUAL, API, or CENTRAL_BANK';
