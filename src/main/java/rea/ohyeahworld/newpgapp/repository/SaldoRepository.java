package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Saldo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class SaldoRepository {
    private final JdbcTemplate jdbc;

    public SaldoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Saldo> findAll() {
        return jdbc.query("""
                SELECT id, apartment_number, period, opening_balance, closing_balance
                FROM saldo
                ORDER BY apartment_number, period
                """, (rs, n) -> new Saldo(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("period").toLocalDate(),
                rs.getBigDecimal("opening_balance"), rs.getBigDecimal("closing_balance")));
    }

    public Optional<Saldo> findById(long id) {
        return jdbc.query("""
                SELECT id, apartment_number, period, opening_balance, closing_balance
                FROM saldo WHERE id = ?
                """, (rs, n) -> new Saldo(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("period").toLocalDate(),
                rs.getBigDecimal("opening_balance"), rs.getBigDecimal("closing_balance")), id)
                .stream().findFirst();
    }

    /** Есть ли уже запись сальдо за эту квартиру и период (защита от дублей). */
    public boolean existsForPeriod(Integer apartmentNumber, LocalDate period) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM saldo WHERE apartment_number = ? AND period = ?
                """, Long.class, apartmentNumber, period);
        return count != null && count > 0;
    }

    /** Исходящее сальдо последнего периода, предшествующего заданному (или 0). */
    public BigDecimal previousClosing(Integer apartmentNumber, LocalDate period) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE((
                    SELECT s.closing_balance FROM saldo s
                    WHERE s.apartment_number = ? AND s.period < date_trunc('month', ?)::DATE
                    ORDER BY s.period DESC LIMIT 1
                ), 0)
                """, BigDecimal.class, apartmentNumber, period);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Входящее сальдо ближайшего следующего периода (если он есть). */
    public Optional<BigDecimal> nextOpening(Integer apartmentNumber, LocalDate period) {
        return jdbc.query("""
                SELECT opening_balance FROM saldo
                WHERE apartment_number = ? AND period > date_trunc('month', ?)::DATE
                ORDER BY period LIMIT 1
                """, (rs, n) -> rs.getBigDecimal("opening_balance"), apartmentNumber, period)
                .stream().findFirst();
    }

    public void insert(Saldo s) {
        jdbc.update("""
                INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance)
                VALUES (?, ?, ?, ?)
                """, s.apartmentNumber(), s.period(), s.openingBalance(), s.closingBalance());
    }

    public void update(Saldo s) {
        jdbc.update("""
                UPDATE saldo
                SET apartment_number=?, period=?, opening_balance=?, closing_balance=?,
                    updated_at = now()
                WHERE id=?
                """, s.apartmentNumber(), s.period(), s.openingBalance(), s.closingBalance(), s.id());
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM saldo WHERE id=?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM saldo", Long.class);
    }
}
