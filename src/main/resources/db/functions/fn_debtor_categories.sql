-- Сводка по категориям должников на указанную дату.
-- Сальдо считается от начала года; при отсутствии строки saldo на начало
-- года входящее восстанавливается из истории начислений/платежей.
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
STABLE
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
           ), (
               SELECT COALESCE(
                   (SELECT SUM(c.amount) FROM charges c
                    WHERE c.apartment_number = a.apartment_number
                      AND date_trunc('year', c.period)::DATE < b.year_start), 0)
                   -
                   (SELECT COALESCE(SUM(p.amount), 0) FROM payments p
                    WHERE p.apartment_number = a.apartment_number
                      AND date_trunc('year', p.payment_date)::DATE < b.year_start)
               FROM bounds b
           ))::NUMERIC(12,2) AS opening_balance,
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
