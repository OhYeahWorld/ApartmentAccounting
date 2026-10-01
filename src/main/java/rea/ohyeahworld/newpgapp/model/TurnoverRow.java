package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;

public record TurnoverRow(Integer apartmentNumber, BigDecimal openingBalance,
                          Integer monthNo, BigDecimal chargesAmount,
                          BigDecimal paymentsAmount, BigDecimal closingBalance) {
}
