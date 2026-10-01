package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;

public record DebtorRow(Integer apartmentNumber, BigDecimal lastCharge,
                        BigDecimal balance, String debtCategory,
                        BigDecimal oneMonth, BigDecimal twoMonths,
                        BigDecimal threeMonths, BigDecimal overThreeMonths) {
}
