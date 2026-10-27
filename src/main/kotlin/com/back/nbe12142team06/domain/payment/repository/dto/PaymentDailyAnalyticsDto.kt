package com.back.nbe12142team06.domain.payment.repository.dto

import java.math.BigDecimal
import java.time.LocalDate

data class PaymentDailyAnalyticsDto(
    val approvedAt: LocalDate,
    val paidAmount: BigDecimal,
    val counts: Long
)
