package rea.ohyeahworld.newpgapp.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import rea.ohyeahworld.newpgapp.model.Charge;
import rea.ohyeahworld.newpgapp.repository.ChargeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Начисления: период нормализуется к началу месяца, повторная отправка
 * одинакового запроса (та же квартира + тот же месяц) отклоняется как дубликат.
 */
@Service
public class ChargeService {
    private final ChargeRepository chargeRepository;

    public ChargeService(ChargeRepository chargeRepository) {
        this.chargeRepository = chargeRepository;
    }

    public void create(Integer apartmentNumber, LocalDate period,
                       BigDecimal amount, String description) {
        LocalDate normalized = SaldoService.normalize(period);
        validate(apartmentNumber, amount);

        if (chargeRepository.existsForPeriod(apartmentNumber, normalized)) {
            throw new DataValidationException(
                    "Начисление за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — повторный запрос отклонен.");
        }
        try {
            chargeRepository.insert(new Charge(null, apartmentNumber, normalized, amount, description));
        } catch (DuplicateKeyException e) {
            throw new DataValidationException(
                    "Начисление за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — повторный запрос отклонен.");
        }
    }

    public void update(Long id, Integer apartmentNumber, LocalDate period,
                       BigDecimal amount, String description) {
        LocalDate normalized = SaldoService.normalize(period);
        validate(apartmentNumber, amount);

        Charge current = chargeRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Начисление не найдено."));
        if ((!current.apartmentNumber().equals(apartmentNumber) || !current.period().equals(normalized))
                && chargeRepository.existsForPeriod(apartmentNumber, normalized)) {
            throw new DataValidationException(
                    "Начисление за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — изменение создало бы дубликат.");
        }
        try {
            chargeRepository.update(new Charge(id, apartmentNumber, normalized, amount, description));
        } catch (DuplicateKeyException e) {
            throw new DataValidationException(
                    "Начисление за квартиру " + apartmentNumber + " и период " + normalized
                            + " уже существует — изменение создало бы дубликат.");
        }
    }

    public void delete(long id) {
        chargeRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Начисление не найдено."));
        chargeRepository.delete(id);
    }

    private void validate(Integer apartmentNumber, BigDecimal amount) {
        if (apartmentNumber == null || apartmentNumber <= 0) {
            throw new DataValidationException("Некорректный номер квартиры.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new DataValidationException("Сумма начисления не может быть отрицательной.");
        }
    }
}
