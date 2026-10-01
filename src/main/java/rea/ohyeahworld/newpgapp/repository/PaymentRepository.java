package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Payment;

import java.util.List;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Payment> findAll() {
        return jdbc.query("""
                SELECT id, apartment_number, payment_date, amount, description
                FROM payments
                ORDER BY payment_date DESC, apartment_number
                """, (rs, n) -> new Payment(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("payment_date").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description")));
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
                SET apartment_number=?, payment_date=?, amount=?, description=?
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
