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
