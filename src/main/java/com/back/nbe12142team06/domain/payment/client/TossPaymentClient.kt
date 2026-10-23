package com.back.nbe12142team06.domain.payment.client

import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import org.springframework.http.ResponseEntity

interface TossPaymentClient {
    fun callApiConfirm(
        tossPaymentKey: String,
        tossOrderId: String,
        amount: String
    ): ResponseEntity<TossConfirmResponse>

    fun callApiCancel(
        cancelReason: String,
        tossPaymentKey: String,
        amount: String
    ): ResponseEntity<TossConfirmResponse>
}
