package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Payment> findAll() {
        return jdbc.query("""
                SELECT id, apartment_number, payment_date, amount, description,
                       created_at, updated_at
                FROM payments
                ORDER BY payment_date DESC, apartment_number
                """, (rs, n) -> new Payment(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("payment_date").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    public Optional<Payment> findById(long id) {
        return jdbc.query("""
                SELECT id, apartment_number, payment_date, amount, description,
                       created_at, updated_at
                FROM payments WHERE id = ?
                """, (rs, n) -> new Payment(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("payment_date").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime()), id)
                .stream().findFirst();
    }

    /** Сумма платежей квартиры за месяц, к которому относится дата. */
    public BigDecimal sumForMonth(Integer apartmentNumber, LocalDate anyDayOfMonth) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE apartment_number = ?
                  AND date_trunc('month', payment_date)::DATE = date_trunc('month', ?)::DATE
                """, BigDecimal.class, apartmentNumber, anyDayOfMonth);
        return value == null ? BigDecimal.ZERO : value;
    }

    public void insert(Payment p) {
        jdbc.update("""
                INSERT INTO payments(apartment_number, payment_date, amount, description)
                VALUES (?, ?, ?, ?)
                """, p.apartmentNumber(), p.paymentDate(), p.amount(), p.description());
    }

    public void update(Payment p) {
        jdbc.update("""
                UPDATE payments
                SET apartment_number=?, payment_date=?, amount=?, description=?, updated_at = now()
                WHERE id=?
                """, p.apartmentNumber(), p.paymentDate(), p.amount(), p.description(), p.id());
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM payments WHERE id=?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
    }
}
