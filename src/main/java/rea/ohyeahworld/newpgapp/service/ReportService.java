package rea.ohyeahworld.newpgapp.service;

import org.springframework.stereotype.Service;
import rea.ohyeahworld.newpgapp.model.ApartmentStatementRow;
import rea.ohyeahworld.newpgapp.model.DebtorRow;
import rea.ohyeahworld.newpgapp.model.ReportViewModels;
import rea.ohyeahworld.newpgapp.model.TurnoverRow;
import rea.ohyeahworld.newpgapp.repository.ReportRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public List<ReportViewModels.TurnoverApartment> turnover(int year) {
        Map<Integer, List<TurnoverRow>> grouped = new LinkedHashMap<>();
        for (TurnoverRow row : repository.turnover(year)) {
            grouped.computeIfAbsent(row.apartmentNumber(), k -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream()
                .map(e -> new ReportViewModels.TurnoverApartment(
                        e.getKey(),
                        e.getValue().isEmpty() ? BigDecimal.ZERO : e.getValue().get(0).openingBalance(),
                        e.getValue()))
                .toList();
    }

    public ReportViewModels.ApartmentStatement apartmentStatement(int apartment, int year) {
        List<ApartmentStatementRow> rows = repository.apartmentStatement(apartment, year);
        BigDecimal opening = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).openingBalance();
        BigDecimal charges = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).yearCharges();
        BigDecimal payments = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).yearPayments();
        BigDecimal ending = rows.isEmpty() ? opening : rows.get(0).endingPayable();
        return new ReportViewModels.ApartmentStatement(
                apartment, year, opening, rows, charges, payments, ending);
    }

    public List<DebtorRow> debtors(java.time.LocalDate asOf) {
        return repository.debtors(asOf);
    }

    public BigDecimal totalDebt(java.time.LocalDate asOf) {
        return repository.totalDebt(asOf);
    }
}
