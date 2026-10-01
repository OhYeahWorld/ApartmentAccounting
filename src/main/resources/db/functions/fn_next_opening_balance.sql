-- Возвращает корректное входящее сальдо квартиры за указанный период:
-- исходящее сальдо предыдущего периода, либо 0, если предыдущих периодов нет.
CREATE OR REPLACE FUNCTION fn_next_opening_balance(p_apartment INTEGER, p_period DATE)
RETURNS NUMERIC(12,2)
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE((
        SELECT s.closing_balance
        FROM saldo s
        WHERE s.apartment_number = p_apartment
          AND s.period < date_trunc('month', p_period)::DATE
        ORDER BY s.period DESC
        LIMIT 1
    ), 0)::NUMERIC(12,2)
$$;
