package com.back.nbe12142team06.domain.payment.repository.dto;

import java.time.LocalDate;

public record PaymentDailyAnalyticsDto(
        LocalDate approvedAt,
        Long paidAmount,
        Long counts
) {
}
