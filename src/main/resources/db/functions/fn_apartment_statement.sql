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
