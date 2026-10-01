package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;
import java.util.List;

public final class ReportViewModels {
    private ReportViewModels() {}

    public record TurnoverApartment(Integer apartmentNumber,
                                    BigDecimal openingBalance,
                                    List<TurnoverRow> months) {
    }

    public record ApartmentStatement(Integer apartmentNumber, int year,
                                     BigDecimal openingBalance,
                                     List<ApartmentStatementRow> months,
                                     BigDecimal yearCharges,
                                     BigDecimal yearPayments,
                                     BigDecimal endingPayable) {
    }
}
