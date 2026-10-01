package rea.ohyeahworld.newpgapp.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import rea.ohyeahworld.newpgapp.model.Saldo;
import rea.ohyeahworld.newpgapp.repository.ChargeRepository;
import rea.ohyeahworld.newpgapp.repository.PaymentRepository;
import rea.ohyeahworld.newpgapp.repository.SaldoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Проверки при создании/изменении сальдо:
 * - период нормализуется к началу месяца (запись создается "в то время",
 *   к которому она относится, а не произвольной датой внутри месяца);
 * - нельзя указывать будущий период относительно текущей даты;
 * - входящее сальдо обязано равняться исходящему предыдущего периода;
 * - исходящее = входящее + начисления месяца - платежи месяца;
 * - повторная отправка одинакового запроса (та же квартира и период)
 *   отклоняется как дубликат.
 */
@Service
public class SaldoService {
    private final SaldoRepository saldoRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;

    public SaldoService(SaldoRepository saldoRepository,
                        ChargeRepository chargeRepository,
                        PaymentRepository paymentRepository) {
        this.saldoRepository = saldoRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
    }

    public void create(Integer apartmentNumber, LocalDate period,
                       BigDecimal openingBalance, BigDecimal closingBalance) {
        LocalDate normalized = normalize(period);
        checkApartment(apartmentNumber);
        checkNotFuture(normalized);

        if (existsSame(apartmentNumber, normalized)) {
            throw new DataValidationException(
                    "Сальдо за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — повторный запрос отклонен.");
        }

        BigDecimal expectedOpening = expectedOpening(apartmentNumber, normalized);
        BigDecimal expectedClosing = expectedClosing(apartmentNumber, normalized, expectedOpening);

        // значения из формы сверяются с эталонными: при расхождении
        // запрос отклоняется, а не «молча» перезаписывается
        if (openingBalance != null && scale(openingBalance).compareTo(expectedOpening) != 0) {
            throw new DataValidationException(
                    "Входящее сальдо должно равняться исходящему предыдущего периода ("
                            + expectedOpening.stripTrailingZeros().toPlainString()
                            + "), получено " + scale(openingBalance).toPlainString() + ".");
        }
        if (closingBalance != null && scale(closingBalance).compareTo(expectedClosing) != 0) {
            throw new DataValidationException(
                    "Исходящее сальдо не сходится: входящее " + expectedOpening.toPlainString()
                            + " + начисления - платежи = " + expectedClosing.toPlainString()
                            + ", получено " + scale(closingBalance).toPlainString() + ".");
        }

        try {
            // если пользователь ввел корректные значения — вставляем ровно их;
            // иначе выше уже брошено исключение с объяснением расхождения
            BigDecimal opening = openingBalance != null ? scale(openingBalance) : expectedOpening;
            BigDecimal closing = closingBalance != null ? scale(closingBalance) : expectedClosing;
            saldoRepository.insert(new Saldo(null, apartmentNumber, normalized,
                    opening, closing, null, null));
        } catch (DuplicateKeyException e) {
            throw new DataValidationException(
                    "Сальдо за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — повторный запрос отклонен.");
        }
    }

    public void update(Long id, Integer apartmentNumber, LocalDate period,
                       BigDecimal openingBalance, BigDecimal closingBalance) {
        LocalDate normalized = normalize(period);
        checkApartment(apartmentNumber);

        Saldo current = saldoRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Запись сальдо не найдена."));

        if (!current.apartmentNumber().equals(apartmentNumber)
                || !current.period().equals(normalized)) {
            if (existsSame(apartmentNumber, normalized)) {
                throw new DataValidationException(
                        "Сальдо за квартиру " + apartmentNumber + " и период " + normalized
                                + " уже существует — изменение создало бы дубликат.");
            }
        }

        BigDecimal expectedOpening = expectedOpening(apartmentNumber, normalized);
        if (scale(openingBalance).compareTo(expectedOpening) != 0) {
            throw new DataValidationException(
                    "Входящее сальдо должно равняться исходящему предыдущего периода: "
                            + expectedOpening.stripTrailingZeros().toPlainString());
        }

        BigDecimal expectedClosing = expectedClosing(apartmentNumber, normalized, expectedOpening);
        if (scale(closingBalance).compareTo(expectedClosing) != 0) {
            throw new DataValidationException(
                    "Исходящее сальдо не сходится: входящее " + expectedOpening.toPlainString()
                            + " + начисления - платежи = " + expectedClosing.toPlainString());
        }

        saldoRepository.nextOpening(apartmentNumber, normalized).ifPresent(nextOpening -> {
            if (nextOpening.compareTo(expectedClosing) != 0) {
                throw new DataValidationException(
                        "Нельзя изменить сальдо: входящее следующего периода ("
                                + nextOpening.toPlainString()
                                + ") должно остаться равным новому исходящему ("
                                + expectedClosing.toPlainString() + ").");
            }
        });

        try {
            saldoRepository.update(new Saldo(id, apartmentNumber, normalized,
                    openingBalance, closingBalance, null, null));
        } catch (DuplicateKeyException e) {
            throw new DataValidationException(
                    "Сальдо за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — изменение создало бы дубликат.");
        }
    }

    public void delete(long id) {
        Saldo current = saldoRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Запись сальдо не найдена."));
        BigDecimal nextOpening = saldoRepository.nextOpening(current.apartmentNumber(), current.period())
                .orElse(null);
        if (nextOpening != null && nextOpening.compareTo(current.closingBalance()) != 0) {
            throw new DataValidationException(
                    "Нельзя удалить запись: входящее следующего периода опирается на это сальдо.");
        }
        saldoRepository.delete(id);
    }

    /** Период приводится к первому числу месяца. */
    public static LocalDate normalize(LocalDate period) {
        if (period == null) {
            throw new DataValidationException("Не указан период.");
        }
        return period.withDayOfMonth(1);
    }

    /**
     * Запись создается «в то время», к которому она относится: дата
     * создания не может лежать в будущем относительно текущей даты.
     */
    static void checkNotFutureDate(LocalDate date, String what) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new DataValidationException(
                    what + " " + date + " лежит в будущем (сегодня " + LocalDate.now()
                            + ") — операция отклонена.");
        }
    }

    private void checkNotFuture(LocalDate period) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        if (period.isAfter(currentMonth)) {
            throw new DataValidationException(
                    "Нельзя создавать сальдо за будущий период " + period
                            + ". Текущий месяц: " + currentMonth + ".");
        }
    }

    private void checkApartment(Integer apartmentNumber) {
        if (apartmentNumber == null || apartmentNumber <= 0) {
            throw new DataValidationException("Некорректный номер квартиры.");
        }
    }

    private boolean existsSame(Integer apartmentNumber, LocalDate period) {
        return saldoRepository.existsForPeriod(apartmentNumber, period);
    }

    /** Исходящее сальдо последнего предшествующего периода (или 0). */
    private BigDecimal expectedOpening(Integer apartmentNumber, LocalDate period) {
        return saldoRepository.previousClosing(apartmentNumber, period);
    }

    /** Входящее + начисления месяца - платежи месяца. */
    private BigDecimal expectedClosing(Integer apartmentNumber, LocalDate period, BigDecimal opening) {
        BigDecimal charges = chargeRepository.sumForMonth(apartmentNumber, period);
        BigDecimal payments = paymentRepository.sumForMonth(apartmentNumber, period);
        return scale(opening.add(charges).subtract(payments));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
