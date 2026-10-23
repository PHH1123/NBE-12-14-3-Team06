package com.back.nbe12142team06.domain.payment.repository.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentDailyAnalyticsDto(
        LocalDate approvedAt,
        BigDecimal paidAmount,
        Long counts
) {
}
