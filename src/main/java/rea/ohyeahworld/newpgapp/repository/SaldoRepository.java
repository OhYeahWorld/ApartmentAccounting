package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.Saldo;

import java.util.List;

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

    public void insert(Saldo s) {
        jdbc.update("""
                INSERT INTO saldo(apartment_number, period, opening_balance, closing_balance)
                VALUES (?, ?, ?, ?)
                """, s.apartmentNumber(), s.period(), s.openingBalance(), s.closingBalance());
    }

    public void update(Saldo s) {
        jdbc.update("""
                UPDATE saldo
                SET apartment_number=?, period=?, opening_balance=?, closing_balance=?
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
