package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Charge(Long id, Integer apartmentNumber, LocalDate period,
                     BigDecimal amount, String description) {
}
