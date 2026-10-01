package rea.ohyeahworld.newpgapp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rea.ohyeahworld.newpgapp.model.ApartmentStatementRow;
import rea.ohyeahworld.newpgapp.model.DebtorRow;
import rea.ohyeahworld.newpgapp.model.TurnoverRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class ReportRepository {
    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TurnoverRow> turnover(int year) {
        return jdbc.query("SELECT * FROM fn_turnover_statement(?)",
                ps -> ps.setInt(1, year),
                (rs, n) -> new TurnoverRow(
                        rs.getInt("apartment_number"),
                        rs.getBigDecimal("opening_balance"),
                        rs.getInt("month_no"),
                        rs.getBigDecimal("charges_amount"),
                        rs.getBigDecimal("payments_amount"),
                        rs.getBigDecimal("closing_balance")));
    }

    public List<ApartmentStatementRow> apartmentStatement(int apartment, int year) {
        return jdbc.query("SELECT * FROM fn_apartment_statement(?, ?)",
                ps -> {
                    ps.setInt(1, apartment);
                    ps.setInt(2, year);
                },
                (rs, n) -> new ApartmentStatementRow(
                        rs.getInt("month_no"),
                        rs.getBigDecimal("charges_amount"),
                        rs.getBigDecimal("payments_amount"),
                        rs.getBigDecimal("closing_balance"),
                        rs.getBigDecimal("opening_balance"),
                        rs.getBigDecimal("year_charges"),
                        rs.getBigDecimal("year_payments"),
                        rs.getBigDecimal("ending_payable")));
    }

    public List<DebtorRow> debtors(LocalDate asOf) {
        return jdbc.query("SELECT * FROM fn_debtor_categories(?)",
                ps -> ps.setObject(1, asOf),
                (rs, n) -> new DebtorRow(
                        rs.getInt("apartment_number"),
                        rs.getBigDecimal("last_charge"),
                        rs.getBigDecimal("balance"),
                        rs.getString("debt_category"),
                        rs.getBigDecimal("one_month"),
                        rs.getBigDecimal("two_months"),
                        rs.getBigDecimal("three_months"),
                        rs.getBigDecimal("over_three_months")));
    }

    public BigDecimal totalDebt(LocalDate asOf) {
        return debtors(asOf).stream()
                .map(DebtorRow::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
