package com.back.nbe12142team06.domain.payment.dto

@JvmRecord
data class PaymentConfirmResponse(
    val orderId: String,
    val paymentKey: String,
    val amount: String
) {
    constructor(request: PaymentConfirmRequest) : this(
        request.orderId,
        request.paymentKey,
        request.amount
    )
}
