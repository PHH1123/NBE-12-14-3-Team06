package com.back.nbe12142team06.domain.payment.dto

@JvmRecord
data class SaveAmountRequest(
    val paymentId: Long,
    val orderId: String,
    val amount: String
)
