package com.back.nbe12142team06.domain.payment.dto

import com.back.nbe12142team06.domain.payment.entity.Payment

data class PaymentAnalyticsResponse(
    val paidAmount: Long,
    val canceledAmount: Long,
    val counts: Long
) {
    constructor(payments: List<Payment>) : this(
        payments.sumOf { it.balanceAmount }.toLong(),
        payments.sumOf { it.amount.subtract(it.balanceAmount) }.toLong(),
        payments.size.toLong()
    )
}
