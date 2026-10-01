package rea.ohyeahworld.newpgapp.service;

import org.springframework.stereotype.Service;
import rea.ohyeahworld.newpgapp.model.Payment;
import rea.ohyeahworld.newpgapp.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Платежи: дата платежа не может относиться к будущему времени
 * (проверка «когда было создано что-то» относительно текущей даты).
 */
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void create(Integer apartmentNumber, LocalDate paymentDate,
                       BigDecimal amount, String description) {
        validate(apartmentNumber, paymentDate, amount);
        paymentRepository.insert(new Payment(null, apartmentNumber, paymentDate, amount, description));
    }

    public void update(Long id, Integer apartmentNumber, LocalDate paymentDate,
                       BigDecimal amount, String description) {
        validate(apartmentNumber, paymentDate, amount);
        paymentRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Платеж не найден."));
        paymentRepository.update(new Payment(id, apartmentNumber, paymentDate, amount, description));
    }

    public void delete(long id) {
        paymentRepository.findById(id)
                .orElseThrow(() -> new DataValidationException("Платеж не найден."));
        paymentRepository.delete(id);
    }

    private void validate(Integer apartmentNumber, LocalDate paymentDate, BigDecimal amount) {
        if (apartmentNumber == null || apartmentNumber <= 0) {
            throw new DataValidationException("Некорректный номер квартиры.");
        }
        if (paymentDate == null) {
            throw new DataValidationException("Не указана дата платежа.");
        }
        if (paymentDate.isAfter(LocalDate.now())) {
            throw new DataValidationException(
                    "Дата платежа " + paymentDate + " лежит в будущем — платеж принят быть не может.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new DataValidationException("Сумма платежа не может быть отрицательной.");
        }
    }
}
