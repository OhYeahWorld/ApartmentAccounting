CREATE TABLE IF NOT EXISTS saldo (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    period DATE NOT NULL,
    opening_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    closing_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT uk_saldo_apartment_period UNIQUE (apartment_number, period)
);

CREATE INDEX IF NOT EXISTS idx_saldo_period ON saldo(period);
CREATE INDEX IF NOT EXISTS idx_saldo_apartment ON saldo(apartment_number);

CREATE TABLE IF NOT EXISTS charges (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    period DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    description VARCHAR(255),
    CONSTRAINT uk_charges_apartment_period UNIQUE (apartment_number, period)
);

CREATE INDEX IF NOT EXISTS idx_charges_period ON charges(period);
CREATE INDEX IF NOT EXISTS idx_charges_apartment ON charges(apartment_number);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    payment_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    description VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_apartment ON payments(apartment_number);
