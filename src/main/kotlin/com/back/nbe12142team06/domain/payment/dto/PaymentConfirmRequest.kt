package com.back.nbe12142team06.domain.payment.dto

data class PaymentConfirmRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: String
)
