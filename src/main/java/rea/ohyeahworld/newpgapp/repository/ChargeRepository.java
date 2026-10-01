package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Charge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ChargeRepository {
    private final JdbcTemplate jdbc;

    public ChargeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Charge> findAll() {
        return jdbc.query("""
                SELECT id, apartment_number, period, amount, description,
                       created_at, updated_at
                FROM charges
                ORDER BY period DESC, apartment_number
                """, (rs, n) -> new Charge(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("period").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime()));
    }

    public Optional<Charge> findById(long id) {
        return jdbc.query("""
                SELECT id, apartment_number, period, amount, description,
                       created_at, updated_at
                FROM charges WHERE id = ?
                """, (rs, n) -> new Charge(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("period").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime()), id)
                .stream().findFirst();
    }

    /** Есть ли начисление за эту квартиру и месяц (защита от повторного запроса). */
    public boolean existsForPeriod(Integer apartmentNumber, LocalDate period) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM charges
                WHERE apartment_number = ?
                  AND date_trunc('month', period)::DATE = date_trunc('month', ?)::DATE
                """, Long.class, apartmentNumber, period);
        return count != null && count > 0;
    }

    /** Сумма начислений квартиры за месяц, к которому относится дата. */
    public BigDecimal sumForMonth(Integer apartmentNumber, LocalDate anyDayOfMonth) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM charges
                WHERE apartment_number = ?
                  AND date_trunc('month', period)::DATE = date_trunc('month', ?)::DATE
                """, BigDecimal.class, apartmentNumber, anyDayOfMonth);
        return value == null ? BigDecimal.ZERO : value;
    }

    public void insert(Charge c) {
        jdbc.update("""
                INSERT INTO charges(apartment_number, period, amount, description)
                VALUES (?, ?, ?, ?)
                """, c.apartmentNumber(), c.period(), c.amount(), c.description());
    }

    public void update(Charge c) {
        jdbc.update("""
                UPDATE charges
                SET apartment_number=?, period=?, amount=?, description=?, updated_at = now()
                WHERE id=?
                """, c.apartmentNumber(), c.period(), c.amount(), c.description(), c.id());
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM charges WHERE id=?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM charges", Long.class);
    }

    public LocalDate latestPeriod() {
        return jdbc.queryForObject("SELECT MAX(period) FROM charges", LocalDate.class);
    }
}
