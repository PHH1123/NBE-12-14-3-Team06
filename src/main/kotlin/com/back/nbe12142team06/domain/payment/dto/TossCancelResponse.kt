package com.back.nbe12142team06.domain.payment.dto

data class TossCancelResponse(
    val method: String,
    val totalAmount: String,
    val balanceAmount: String,
    val paymentKey: String,
    val orderId: String,
)
