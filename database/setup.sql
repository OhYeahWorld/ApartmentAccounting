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

CREATE OR REPLACE FUNCTION fn_turnover_statement(p_year INTEGER)
RETURNS TABLE (
    apartment_number INTEGER,
    opening_balance NUMERIC(12,2),
    month_no INTEGER,
    charges_amount NUMERIC(12,2),
    payments_amount NUMERIC(12,2),
    closing_balance NUMERIC(12,2)
)
LANGUAGE sql
AS $$
WITH months AS (
    SELECT generate_series(1, 12)::INTEGER AS month_no
), apartments AS (
    SELECT apartment_number FROM saldo
    UNION
    SELECT apartment_number FROM charges
    UNION
    SELECT apartment_number FROM payments
), opening AS (
    SELECT a.apartment_number,
           COALESCE((
               SELECT s.opening_balance
               FROM saldo s
               WHERE s.apartment_number = a.apartment_number
                 AND s.period = make_date(p_year, 1, 1)
               LIMIT 1
           ), 0)::NUMERIC(12,2) AS opening_balance
    FROM apartments a
), monthly AS (
    SELECT o.apartment_number,
           o.opening_balance,
           m.month_no,
           COALESCE((
               SELECT SUM(c.amount)
               FROM charges c
               WHERE c.apartment_number = o.apartment_number
                 AND EXTRACT(YEAR FROM c.period)::INTEGER = p_year
                 AND EXTRACT(MONTH FROM c.period)::INTEGER = m.month_no
           ), 0)::NUMERIC(12,2) AS charges_amount,
           COALESCE((
               SELECT SUM(p.amount)
               FROM payments p
               WHERE p.apartment_number = o.apartment_number
                 AND EXTRACT(YEAR FROM p.payment_date)::INTEGER = p_year
                 AND EXTRACT(MONTH FROM p.payment_date)::INTEGER = m.month_no
           ), 0)::NUMERIC(12,2) AS payments_amount
    FROM opening o
    CROSS JOIN months m
), calculated AS (
    SELECT apartment_number,
           opening_balance,
           month_no,
           charges_amount,
           payments_amount,
           opening_balance + SUM(charges_amount - payments_amount)
               OVER (PARTITION BY apartment_number ORDER BY month_no
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS closing_balance
    FROM monthly
)
SELECT apartment_number,
       ROUND(opening_balance, 2),
       month_no,
       ROUND(charges_amount, 2),
       ROUND(payments_amount, 2),
       ROUND(closing_balance, 2)
FROM calculated
ORDER BY apartment_number, month_no
$$;

CREATE OR REPLACE FUNCTION fn_apartment_statement(p_apartment INTEGER, p_year INTEGER)
RETURNS TABLE (
    month_no INTEGER,
    charges_amount NUMERIC(12,2),
    payments_amount NUMERIC(12,2),
    closing_balance NUMERIC(12,2),
    opening_balance NUMERIC(12,2),
    year_charges NUMERIC(12,2),
    year_payments NUMERIC(12,2),
    ending_payable NUMERIC(12,2)
)
LANGUAGE sql
AS $$
WITH months AS (
    SELECT generate_series(1, 12)::INTEGER AS month_no
), opening AS (
    SELECT COALESCE((
        SELECT s.opening_balance
        FROM saldo s
        WHERE s.apartment_number = p_apartment
          AND s.period = make_date(p_year, 1, 1)
        LIMIT 1
    ), 0)::NUMERIC(12,2) AS opening_balance
), monthly AS (
    SELECT m.month_no,
           o.opening_balance,
           COALESCE((
               SELECT SUM(c.amount)
               FROM charges c
               WHERE c.apartment_number = p_apartment
                 AND EXTRACT(YEAR FROM c.period)::INTEGER = p_year
                 AND EXTRACT(MONTH FROM c.period)::INTEGER = m.month_no
           ), 0)::NUMERIC(12,2) AS charges_amount,
           COALESCE((
               SELECT SUM(p.amount)
               FROM payments p
               WHERE p.apartment_number = p_apartment
                 AND EXTRACT(YEAR FROM p.payment_date)::INTEGER = p_year
                 AND EXTRACT(MONTH FROM p.payment_date)::INTEGER = m.month_no
           ), 0)::NUMERIC(12,2) AS payments_amount
    FROM months m
    CROSS JOIN opening o
), calculated AS (
    SELECT month_no,
           charges_amount,
           payments_amount,
           opening_balance,
           opening_balance + SUM(charges_amount - payments_amount)
               OVER (ORDER BY month_no ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS closing_balance
    FROM monthly
), totals AS (
    SELECT SUM(charges_amount)::NUMERIC(12,2) AS year_charges,
           SUM(payments_amount)::NUMERIC(12,2) AS year_payments
    FROM calculated
)
SELECT c.month_no,
       ROUND(c.charges_amount, 2),
       ROUND(c.payments_amount, 2),
       ROUND(c.closing_balance, 2),
       ROUND(c.opening_balance, 2),
       ROUND(t.year_charges, 2),
       ROUND(t.year_payments, 2),
       ROUND(c.opening_balance + t.year_charges - t.year_payments, 2)
FROM calculated c
CROSS JOIN totals t
ORDER BY c.month_no
$$;

CREATE OR REPLACE FUNCTION fn_debtor_categories(p_as_of DATE)
RETURNS TABLE (
    apartment_number INTEGER,
    last_charge NUMERIC(12,2),
    balance NUMERIC(12,2),
    debt_category VARCHAR(40),
    one_month NUMERIC(12,2),
    two_months NUMERIC(12,2),
    three_months NUMERIC(12,2),
    over_three_months NUMERIC(12,2)
)
LANGUAGE sql
AS $$
WITH bounds AS (
    SELECT date_trunc('year', p_as_of)::DATE AS year_start,
           date_trunc('month', p_as_of)::DATE AS month_start
), apartments AS (
    SELECT apartment_number FROM saldo
    UNION
    SELECT apartment_number FROM charges
    UNION
    SELECT apartment_number FROM payments
), base AS (
    SELECT a.apartment_number,
           COALESCE((
               SELECT s.opening_balance
               FROM saldo s, bounds b
               WHERE s.apartment_number = a.apartment_number
                 AND s.period = b.year_start
               LIMIT 1
           ), 0)::NUMERIC(12,2) AS opening_balance,
           COALESCE((
               SELECT SUM(c.amount)
               FROM charges c, bounds b
               WHERE c.apartment_number = a.apartment_number
                 AND c.period >= b.year_start
                 AND c.period < b.month_start
           ), 0)::NUMERIC(12,2) AS charges_before,
           COALESCE((
               SELECT SUM(p.amount)
               FROM payments p, bounds b
               WHERE p.apartment_number = a.apartment_number
                 AND p.payment_date >= b.year_start
                 AND p.payment_date < b.month_start
           ), 0)::NUMERIC(12,2) AS payments_before,
           COALESCE((
               SELECT c.amount
               FROM charges c, bounds b
               WHERE c.apartment_number = a.apartment_number
                 AND c.period >= b.year_start
                 AND c.period < b.month_start
               ORDER BY c.period DESC
               LIMIT 1
           ), 0)::NUMERIC(12,2) AS last_charge
    FROM apartments a
), balances AS (
    SELECT apartment_number,
           ROUND(last_charge, 2) AS last_charge,
           ROUND(opening_balance + charges_before - payments_before, 2) AS balance
    FROM base
), categorized AS (
    SELECT apartment_number,
           last_charge,
           balance,
           CASE
               WHEN balance <= 0 THEN 'Нет задолженности'
               WHEN last_charge <= 0 THEN 'Свыше 3 месяцев'
               WHEN CEIL(balance / NULLIF(last_charge, 0)) = 1 THEN '1 месяц'
               WHEN CEIL(balance / NULLIF(last_charge, 0)) = 2 THEN '2 месяца'
               WHEN CEIL(balance / NULLIF(last_charge, 0)) = 3 THEN '3 месяца'
               ELSE 'Свыше 3 месяцев'
           END::VARCHAR(40) AS debt_category
    FROM balances
)
SELECT apartment_number,
       last_charge,
       balance,
       debt_category,
       CASE WHEN debt_category = '1 месяц' THEN balance ELSE 0 END,
       CASE WHEN debt_category = '2 месяца' THEN balance ELSE 0 END,
       CASE WHEN debt_category = '3 месяца' THEN balance ELSE 0 END,
       CASE WHEN debt_category = 'Свыше 3 месяцев' THEN balance ELSE 0 END
FROM categorized
WHERE balance > 0
ORDER BY apartment_number
$$;

INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (1, DATE '2017-01-01', 5379.37, 5379.37);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (2, DATE '2017-01-01', 14476.86, 14476.86);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (3, DATE '2017-01-01', 3591.62, 3591.62);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (4, DATE '2017-01-01', 8543.02, 8543.02);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (5, DATE '2017-01-01', 3107.40, 3107.40);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (6, DATE '2017-01-01', -510.53, -510.53);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (12, DATE '2017-01-01', 0.00, 0.00);
INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance) VALUES (14, DATE '2017-01-01', 0.00, 0.00);

INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-01-01', 4590.22, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-01-15', 4004.06, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-02-01', 4589.64, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-02-15', 4004.06, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-03-01', 5030.62, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-03-15', 586.16, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-04-01', 4502.41, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-04-15', 6405.35, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-05-01', 4405.16, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-05-15', 3127.10, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-06-01', 4030.90, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-06-15', 4405.16, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-07-01', 4528.61, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-07-15', 4030.90, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-08-01', 4579.44, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-08-15', 4528.61, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-09-01', 4851.68, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-09-15', 4579.44, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (1, DATE '2017-10-01', 4823.83, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (1, DATE '2017-10-15', 4851.68, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-01-01', 4870.26, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-02-01', 4880.88, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (2, DATE '2017-02-15', 4345.70, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-03-01', 6522.06, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-04-01', 5095.99, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-05-01', 4829.47, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-06-01', 4137.06, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-07-01', 4988.47, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-08-01', 4788.85, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-09-01', 5082.79, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (2, DATE '2017-10-01', 5111.77, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-01-01', 2430.33, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-02-01', 3120.22, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (3, DATE '2017-02-15', 6000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-03-01', 3497.19, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-04-01', 2051.45, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (3, DATE '2017-04-15', 6200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-05-01', 2988.59, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-06-01', 2593.99, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (3, DATE '2017-06-15', 8000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-07-01', 3114.73, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-08-01', 3114.73, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (3, DATE '2017-08-15', 6400.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-09-01', 2753.53, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (3, DATE '2017-10-01', 3330.68, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-01-01', 4633.43, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-02-01', 6002.58, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-02-15', 11223.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-03-01', 6756.52, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-03-15', 442.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-04-01', 3916.04, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-04-15', 12956.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-05-01', 5790.33, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-06-01', 5001.12, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-06-15', 9706.37, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-07-01', 5977.39, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-07-15', 5001.12, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-08-01', 5977.39, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-08-15', 7391.94, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-09-01', 5254.99, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (4, DATE '2017-10-01', 6409.30, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (4, DATE '2017-10-15', 11232.38, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-01-01', 5961.25, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (5, DATE '2017-01-15', 10170.50, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-02-01', 5971.87, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-03-01', 7047.40, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-04-01', 6018.74, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (5, DATE '2017-04-15', 11917.42, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-05-01', 4257.83, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-06-01', 3923.09, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-07-01', 5629.70, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (5, DATE '2017-07-15', 19829.36, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-08-01', 5670.83, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (5, DATE '2017-08-15', 9552.79, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-09-01', 6006.69, 'ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (5, DATE '2017-10-01', 4424.71, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (5, DATE '2017-10-15', 14701.28, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-01-01', 4204.64, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-01-15', 4000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-02-01', 4125.26, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-02-15', 4000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-03-01', 5400.51, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-03-15', 4000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-04-01', 4450.48, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-04-15', 4300.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-05-01', 4180.72, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-05-15', 5300.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-06-01', 3909.60, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-06-15', 4600.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-07-01', 4057.27, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-07-15', 4500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-08-01', 4481.84, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-08-15', 4500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-09-01', 4739.99, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-09-15', 4000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (6, DATE '2017-10-01', 4466.83, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (6, DATE '2017-10-15', 4000.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-01-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-01-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-02-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-02-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-03-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-03-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-04-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-04-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-05-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-05-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-06-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-06-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-07-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-07-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-08-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-08-15', 2500.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (12, DATE '2017-09-01', 6305.35, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (12, DATE '2017-09-15', 788.40, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-01-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-01-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-02-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-02-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-03-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-03-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-04-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-04-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-05-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-05-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-06-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-06-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-07-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-07-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-08-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-08-15', 2200.00, 'Оплата ЖКУ');
INSERT INTO charges(apartment_number, period, amount, description) VALUES (14, DATE '2017-09-01', 2900.55, 'ЖКУ');
INSERT INTO payments(apartment_number, payment_date, amount, description) VALUES (14, DATE '2017-09-15', 1014.69, 'Оплата ЖКУ');
