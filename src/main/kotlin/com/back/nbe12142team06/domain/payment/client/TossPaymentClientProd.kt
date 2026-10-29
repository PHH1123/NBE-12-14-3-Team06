package com.back.nbe12142team06.domain.payment.client

import com.back.nbe12142team06.domain.payment.dto.TossCancelResponse
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import tools.jackson.databind.ObjectMapper

@Component
@Profile("prod")
class TossPaymentClientProd(
    private val tossRestClient: RestClient,
    private val objectMapper: ObjectMapper
) : TossPaymentClient {

    private val log = LoggerFactory.getLogger(TossPaymentClientProd::class.java)

    override fun callApiConfirm(
        tossPaymentKey: String,
        tossOrderId: String,
        amount: String
    ): ResponseEntity<TossConfirmResponse> {

        val requestBody = objectMapper.createObjectNode()
            .put("paymentKey", tossPaymentKey)
            .put("orderId", tossOrderId)
            .put("amount", amount)
            .toPrettyString()

        val response: ResponseEntity<TossConfirmResponse>
        try {
            response = tossRestClient.run {
                post()
                    .uri("/v1/payments/confirm")
                    .body(requestBody)
                    .retrieve()
                    .toEntity<TossConfirmResponse>()
            }
        } catch (e: RuntimeException) {
            throw InternalServerErrorException(43, e.message!!)
        }

        if (!response.statusCode.is2xxSuccessful) {
            throw InternalServerErrorException(43, "토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다.")
        }

        log.info("토스 결제 승인 요청 성공 - tossOrderId: {}, amount: {}", tossOrderId, amount)

        return response
    }

    override fun callApiCancel(
        cancelReason: String,
        tossPaymentKey: String,
        amount: String
    ): ResponseEntity<TossCancelResponse> {

        val requestBody = objectMapper.createObjectNode()
            .put("cancelReason", cancelReason)
            .put("cancelAmount", amount)
            .toPrettyString()

        val response: ResponseEntity<TossCancelResponse>
        try {
            response = tossRestClient.run {
                post()
                    .uri("/v1/payments/$tossPaymentKey/cancel")
                    .body(requestBody)
                    .retrieve()
                    .toEntity<TossCancelResponse>()
            }
        } catch (e: RuntimeException) {
            throw InternalServerErrorException(44, e.message!!)
        }

        if (!response.statusCode.is2xxSuccessful) {
            throw InternalServerErrorException(44, "토스 결제 취소 API 호출 실패, 결제 취소에 실패했습니다.")
        }

        log.info("토스 결제 취소 요청 성공 - cancelReason: {}, amount: {}", cancelReason, amount)

        return response
    }
}
