package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Charge;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ChargeRepository {
    private final JdbcTemplate jdbc;

    public ChargeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Charge> findAll() {
        return jdbc.query("""
                SELECT id, apartment_number, period, amount, description
                FROM charges
                ORDER BY period DESC, apartment_number
                """, (rs, n) -> new Charge(
                rs.getLong("id"), rs.getInt("apartment_number"),
                rs.getDate("period").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("description")));
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
                SET apartment_number=?, period=?, amount=?, description=?
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
