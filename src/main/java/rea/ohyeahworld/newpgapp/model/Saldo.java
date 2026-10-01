package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Saldo(Long id, Integer apartmentNumber, LocalDate period,
                    BigDecimal openingBalance, BigDecimal closingBalance) {
}
