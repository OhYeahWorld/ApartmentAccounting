package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;

public record ApartmentStatementRow(Integer monthNo, BigDecimal chargesAmount,
                                    BigDecimal paymentsAmount,
                                    BigDecimal closingBalance,
                                    BigDecimal openingBalance,
                                    BigDecimal yearCharges,
                                    BigDecimal yearPayments,
                                    BigDecimal endingPayable) {
}
