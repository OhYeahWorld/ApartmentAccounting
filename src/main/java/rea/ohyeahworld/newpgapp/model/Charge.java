package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Charge(Long id, Integer apartmentNumber, LocalDate period,
                     BigDecimal amount, String description,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
}
