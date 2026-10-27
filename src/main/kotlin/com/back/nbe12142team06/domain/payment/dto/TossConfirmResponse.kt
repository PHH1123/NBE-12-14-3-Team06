package com.back.nbe12142team06.domain.payment.dto

data class TossConfirmResponse(
    val method: String,
    val totalAmount: String,
    val paymentKey: String,
    val orderId: String,
)
