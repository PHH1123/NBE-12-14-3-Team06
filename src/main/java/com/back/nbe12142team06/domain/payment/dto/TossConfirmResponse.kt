package com.back.nbe12142team06.domain.payment.dto

@JvmRecord
data class TossConfirmResponse(
    val method: String,
    val totalAmount: String
)
