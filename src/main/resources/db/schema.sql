-- ============================================================
-- Таблица сальдо. Период всегда хранится как первый день месяца.
-- ============================================================
CREATE TABLE IF NOT EXISTS saldo (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    period DATE NOT NULL DEFAULT date_trunc('month', CURRENT_DATE)::DATE,
    opening_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    closing_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    -- одна запись на квартиру за период (дубликаты запросов отсекает БД)
    CONSTRAINT uk_saldo_apartment_period UNIQUE (apartment_number, period),
    -- период — всегда начало месяца
    CONSTRAINT ck_saldo_period_month_start CHECK (period = date_trunc('month', period)::DATE),
    CONSTRAINT ck_saldo_apartment_positive CHECK (apartment_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_saldo_period ON saldo(period);
CREATE INDEX IF NOT EXISTS idx_saldo_apartment ON saldo(apartment_number);

-- Начисления: период тоже нормализуется к началу месяца,
-- поэтому уникальность (квартира, месяц) реально защищает от повторной
-- отправки одинакового начисления за один и тот же месяц.
CREATE TABLE IF NOT EXISTS charges (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    period DATE NOT NULL DEFAULT date_trunc('month', CURRENT_DATE)::DATE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    CONSTRAINT uk_charges_apartment_period UNIQUE (apartment_number, period),
    CONSTRAINT ck_charges_period_month_start CHECK (period = date_trunc('month', period)::DATE),
    CONSTRAINT ck_charges_apartment_positive CHECK (apartment_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_charges_period ON charges(period);
CREATE INDEX IF NOT EXISTS idx_charges_apartment ON charges(apartment_number);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    apartment_number INTEGER NOT NULL,
    payment_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    CONSTRAINT ck_payments_apartment_positive CHECK (apartment_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_apartment ON payments(apartment_number);

-- ============================================================
-- Временные ограничения (CURRENT_DATE = «текущее время» БД):
-- нельзя внести начисление за будущий период и платеж за будущую дату.
-- ============================================================
ALTER TABLE charges DROP CONSTRAINT IF EXISTS ck_charges_period_not_future;
ALTER TABLE charges ADD CONSTRAINT ck_charges_period_not_future
    CHECK (period <= date_trunc('month', CURRENT_DATE)::DATE);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_date_not_future;
ALTER TABLE payments ADD CONSTRAINT ck_payments_date_not_future
    CHECK (payment_date <= CURRENT_DATE);

-- ============================================================
-- Суммы за месяц (вспомогательная IMMUTABLE-функция нужна для того,
-- чтобы использовать её в табличном CHECK-ограничении:
-- обычные подзапросы в CHECK запрещены).
-- Определяется ПОСЛЕ создания таблиц charges/payments — иначе при
-- CREATE FUNCTION на чистой базе LANGUAGE sql проверит тело и упадёт
-- с "relation \"charges\" does not exist".
-- ============================================================
CREATE OR REPLACE FUNCTION fn_month_movement(p_apartment INTEGER, p_period DATE)
RETURNS NUMERIC(12,2)
LANGUAGE sql STABLE
AS $fn$
    SELECT ROUND(
        COALESCE((SELECT SUM(c.amount) FROM charges c
                  WHERE c.apartment_number = p_apartment
                    AND date_trunc('month', c.period)::DATE = p_period), 0)
      - COALESCE((SELECT SUM(p.amount) FROM payments p
                  WHERE p.apartment_number = p_apartment
                    AND date_trunc('month', p.payment_date)::DATE = p_period), 0), 2)
$fn$;

-- Инвариант БД: исходящее сальдо = входящее + начисления месяца - платежи месяца.
-- CHECK добавляем после того, как таблицы charges/payments точно существуют.
ALTER TABLE saldo DROP CONSTRAINT IF EXISTS ck_saldo_closing_formula;
ALTER TABLE saldo ADD CONSTRAINT ck_saldo_closing_formula CHECK (
    closing_balance = opening_balance + fn_month_movement(apartment_number, period)
);

-- ============================================================
-- Триггеры сальдо:
-- 1) нормализация периода к началу месяца;
-- 2) входящее сальдо обязано равняться исходящему предыдущего периода;
-- 3) исходящее = входящее + начисления месяца - платежи месяца;
-- 4) нельзя создать запись за период раньше уже существующих или
--    вставить дубликат по (квартира, период).
-- ============================================================
CREATE OR REPLACE FUNCTION trg_saldo_before_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_expected_opening NUMERIC(12,2);
    v_prev_period      DATE;
    v_new_period       DATE := date_trunc('month', NEW.period)::DATE;
    v_charges          NUMERIC(12,2);
    v_payments         NUMERIC(12,2);
    v_actual_opening   NUMERIC(12,2);
BEGIN
    -- период не может лежать в будущем относительно текущей даты
    IF v_new_period > date_trunc('month', CURRENT_DATE)::DATE THEN
        RAISE EXCEPTION 'Нельзя создавать сальдо за будущий период %', v_new_period;
    END IF;

    -- входящее сальдо = исходящее сальдо предыдущего периода (или 0)
    SELECT s.period, s.closing_balance
      INTO v_prev_period, v_expected_opening
    FROM saldo s
    WHERE s.apartment_number = NEW.apartment_number
      AND s.period < v_new_period
    ORDER BY s.period DESC
    LIMIT 1;

    v_expected_opening := COALESCE(v_expected_opening, 0);

    IF TG_OP = 'INSERT' THEN
        IF EXISTS (SELECT 1 FROM saldo s
                   WHERE s.apartment_number = NEW.apartment_number
                     AND s.period = v_new_period) THEN
            RAISE EXCEPTION 'Сальдо за квартиру % и период % уже существует',
                NEW.apartment_number, v_new_period;
        END IF;
        -- нельзя вставить период раньше самой ранней существующей записи
        IF v_prev_period IS NULL AND
           EXISTS (SELECT 1 FROM saldo s
                   WHERE s.apartment_number = NEW.apartment_number
                     AND s.period > v_new_period) THEN
            RAISE EXCEPTION 'Нельзя добавить период % раньше уже существующих периодов', v_new_period;
        END IF;
        -- входящее и исходящее всегда вычисляются сами: значения из
        -- формы перезаписываются корректными, поэтому два одинаковых
        -- объекта или запись с несходящимся сальдо создать невозможно.
        NEW.opening_balance := v_expected_opening;
        NEW.closing_balance := ROUND(v_expected_opening + v_charges - v_payments, 2);
    ELSIF EXISTS (SELECT 1 FROM saldo s
                  WHERE s.apartment_number = NEW.apartment_number
                    AND s.period = v_new_period
                    AND s.id <> NEW.id) THEN
        RAISE EXCEPTION 'Сальдо за квартиру % и период % уже существует',
            NEW.apartment_number, v_new_period;
    END IF;

    -- начисления и платежи месяца
    SELECT COALESCE(SUM(c.amount), 0) INTO v_charges
    FROM charges c
    WHERE c.apartment_number = NEW.apartment_number
      AND date_trunc('month', c.period)::DATE = v_new_period;

    SELECT COALESCE(SUM(p.amount), 0) INTO v_payments
    FROM payments p
    WHERE p.apartment_number = NEW.apartment_number
      AND date_trunc('month', p.payment_date)::DATE = v_new_period;

    IF TG_OP = 'UPDATE' THEN
        -- при изменении нельзя разъезжать с предыдущим периодом.
        -- В BEFORE-триггере OLD и NEW могут указывать на одну и ту же
        -- физическую строку, поэтому фактическое входящее берем по id
        -- из таблицы (если запись уже сохранена) — так подмена
        -- opening_balance "в обход" предыдущего периода не пройдет.
        v_actual_opening := COALESCE(
            (SELECT s.opening_balance FROM saldo s WHERE s.id = NEW.id),
            OLD.opening_balance);

        IF v_prev_period IS NOT NULL
           AND ROUND(v_actual_opening, 2) <> v_expected_opening THEN
            RAISE EXCEPTION
                'Входящее сальдо (%) не совпадает с исходящим предыдущего периода (%)',
                ROUND(v_actual_opening, 2), v_expected_opening;
        END IF;
        IF ROUND(NEW.closing_balance, 2)
           <> ROUND(v_expected_opening + v_charges - v_payments, 2) THEN
            RAISE EXCEPTION
                'Исходящее сальдо (%) не сходится: входящее (%) + начисления (%) - платежи (%) = %',
                ROUND(NEW.closing_balance, 2), v_expected_opening, v_charges, v_payments,
                ROUND(v_expected_opening + v_charges - v_payments, 2);
        END IF;
    ELSE
        -- при создании запись всегда корректна по построению:
        -- входящее берется из предыдущего периода,
        -- исходящее = входящее + начисления - платежи месяца.
        NEW.opening_balance := v_expected_opening;
        NEW.closing_balance := ROUND(v_expected_opening + v_charges - v_payments, 2);
    END IF;

    NEW.period := v_new_period;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS saldo_before_insert ON saldo;
CREATE TRIGGER saldo_before_insert
    BEFORE INSERT ON saldo
    FOR EACH ROW EXECUTE FUNCTION trg_saldo_before_write();

DROP TRIGGER IF EXISTS saldo_before_update ON saldo;
CREATE TRIGGER saldo_before_update
    BEFORE UPDATE ON saldo
    FOR EACH ROW EXECUTE FUNCTION trg_saldo_before_write();

-- При изменении сальдо нельзя разъезжать с соседними периодами:
-- входящее следующего периода обязано равняться новому исходящему.
CREATE OR REPLACE FUNCTION trg_saldo_after_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_next RECORD;
BEGIN
    SELECT s.id, s.opening_balance INTO v_next
    FROM saldo s
    WHERE s.apartment_number = NEW.apartment_number
      AND s.period > NEW.period
    ORDER BY s.period
    LIMIT 1;

    IF FOUND AND v_next.opening_balance IS DISTINCT FROM NEW.closing_balance THEN
        RAISE EXCEPTION
            'Нельзя изменить сальдо: входящее сальдо следующего периода (%) не равно новому исходящему (%)',
            v_next.opening_balance, NEW.closing_balance;
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS saldo_after_update ON saldo;
CREATE TRIGGER saldo_after_update
    AFTER UPDATE ON saldo
    FOR EACH ROW EXECUTE FUNCTION trg_saldo_after_write();

DROP TRIGGER IF EXISTS payments_normalize_date ON payments;
-- Платеж не может быть внесён задним числом «в будущее»: дата платежа
-- обязана быть не позже текущей даты (CURRENT_DATE — время сервера БД).
CREATE OR REPLACE FUNCTION trg_payments_before_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.payment_date > CURRENT_DATE THEN
        RAISE EXCEPTION 'Дата платежа % лежит в будущем (сегодня %)',
            NEW.payment_date, CURRENT_DATE;
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS payments_before_insert ON payments;
CREATE TRIGGER payments_before_insert
    BEFORE INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION trg_payments_before_write();

DROP TRIGGER IF EXISTS payments_before_update ON payments;
CREATE TRIGGER payments_before_update
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION trg_payments_before_write();

-- ============================================================
-- После изменения начислений/платежей пересчитываем цепочку сальдо:
-- исходящее каждого периода = входящее + начисления - платежи месяца,
-- а входящее следующего периода = исходящее предыдущего.
-- Так "исходящее за период" всегда равно "входящему в следующий".
-- ============================================================
CREATE OR REPLACE FUNCTION fn_recalc_saldo_chain(p_apartment INTEGER)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    r              RECORD;
    cur            NUMERIC(12,2);
    calc           NUMERIC(12,2);
    v_prev_closing NUMERIC(12,2) := 0;
BEGIN
    FOR r IN SELECT id, period, opening_balance
             FROM saldo
             WHERE apartment_number = p_apartment
             ORDER BY period
    LOOP
        -- ВАЖНО: курсор RECORD при UPDATE той же строки внутри цикла
        -- обнуляет поля (r.id/r.period становятся NULL), поэтому сразу
        -- копируем все нужные значения в локальные переменные.
        DECLARE
            v_id      BIGINT := r.id;
            v_period  DATE   := r.period;
        BEGIN
            cur := COALESCE(r.opening_balance, 0);

            -- страховка для записей, появившихся мимо приложения:
            -- входящее периода обязано равняться исходящему предыдущего
            IF cur <> v_prev_closing THEN
                UPDATE saldo SET opening_balance = v_prev_closing WHERE id = v_id;
                cur := v_prev_closing;
            END IF;

            SELECT ROUND(cur
                   + COALESCE((SELECT SUM(c.amount) FROM charges c
                               WHERE c.apartment_number = p_apartment
                                 AND date_trunc('month', c.period)::DATE = v_period), 0)
                   - COALESCE((SELECT SUM(p.amount) FROM payments p
                               WHERE p.apartment_number = p_apartment
                                 AND date_trunc('month', p.payment_date)::DATE = v_period), 0), 2)
              INTO calc;

            UPDATE saldo SET closing_balance = calc WHERE id = v_id;

            -- подтягиваем входящее следующего периода к новому исходящему
            UPDATE saldo SET opening_balance = calc
            WHERE apartment_number = p_apartment
              AND period = (v_period + INTERVAL '1 month')::TIMESTAMP::DATE;

            v_prev_closing := calc;
        END;
    END LOOP;
END
$$;

CREATE OR REPLACE FUNCTION trg_charges_after_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM fn_recalc_saldo_chain(NEW.apartment_number);
        IF TG_OP = 'UPDATE' AND NEW.apartment_number <> OLD.apartment_number THEN
            PERFORM fn_recalc_saldo_chain(OLD.apartment_number);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM fn_recalc_saldo_chain(OLD.apartment_number);
    END IF;
    RETURN COALESCE(NEW, OLD);
EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Изменение начисления нарушило бы баланс сальдо: %', SQLERRM;
END
$$;

DROP TRIGGER IF EXISTS charges_after_ins ON charges;
CREATE TRIGGER charges_after_ins
    AFTER INSERT ON charges
    FOR EACH ROW EXECUTE FUNCTION trg_charges_after_write();

DROP TRIGGER IF EXISTS charges_after_upd ON charges;
CREATE TRIGGER charges_after_upd
    AFTER UPDATE ON charges
    FOR EACH ROW EXECUTE FUNCTION trg_charges_after_write();

DROP TRIGGER IF EXISTS charges_after_del ON charges;
CREATE TRIGGER charges_after_del
    AFTER DELETE ON charges
    FOR EACH ROW EXECUTE FUNCTION trg_charges_after_write();

CREATE OR REPLACE FUNCTION trg_payments_after_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM fn_recalc_saldo_chain(NEW.apartment_number);
        IF TG_OP = 'UPDATE' AND NEW.apartment_number <> OLD.apartment_number THEN
            PERFORM fn_recalc_saldo_chain(OLD.apartment_number);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM fn_recalc_saldo_chain(OLD.apartment_number);
    END IF;
    RETURN COALESCE(NEW, OLD);
EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'Изменение платежа нарушило бы баланс сальдо: %', SQLERRM;
END
$$;

DROP TRIGGER IF EXISTS payments_after_ins ON payments;
CREATE TRIGGER payments_after_ins
    AFTER INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION trg_payments_after_write();

DROP TRIGGER IF EXISTS payments_after_upd ON payments;
CREATE TRIGGER payments_after_upd
    AFTER UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION trg_payments_after_write();

DROP TRIGGER IF EXISTS payments_after_del ON payments;
CREATE TRIGGER payments_after_del
    AFTER DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION trg_payments_after_write();

-- ============================================================
-- Нормализация периода начислений при вставке/изменении
-- (любая дата внутри месяца приводится к первому числу месяца).
-- ============================================================
CREATE OR REPLACE FUNCTION trg_charges_normalize_period()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.period := date_trunc('month', NEW.period)::DATE;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS charges_normalize_period ON charges;
CREATE TRIGGER charges_normalize_period
    BEFORE INSERT OR UPDATE ON charges
    FOR EACH ROW EXECUTE FUNCTION trg_charges_normalize_period();
