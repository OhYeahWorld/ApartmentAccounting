package rea.ohyeahworld.newpgapp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Payment(Long id, Integer apartmentNumber, LocalDate paymentDate,
                      BigDecimal amount, String description,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
}
