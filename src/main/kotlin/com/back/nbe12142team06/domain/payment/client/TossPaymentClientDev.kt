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
@Profile("dev", "test")
class TossPaymentClientDev(
    private val tossRestClient: RestClient,
    private val objectMapper: ObjectMapper
) : TossPaymentClient {

    private val log = LoggerFactory.getLogger(TossPaymentClientDev::class.java)

    private fun isSeedPaymentKey(tossPaymentKey: String): Boolean {
        return tossPaymentKey.startsWith(SEED_PAYMENT_KEY_PREFIX)
    }

    override fun callApiConfirm(
        tossPaymentKey: String,
        tossOrderId: String,
        amount: String
    ): ResponseEntity<TossConfirmResponse> {
        if (isSeedPaymentKey(tossPaymentKey)) {
            log.info(
                "[시드 결제] 실제 토스 API 호출 없이 승인 처리 - tossOrderId: {}, amount: {}",
                tossOrderId,
                amount
            )
            return ResponseEntity.ok(TossConfirmResponse("카드", amount, tossPaymentKey, tossOrderId))
        }

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
        if (isSeedPaymentKey(tossPaymentKey)) {
            log.info(
                "[시드 결제] 실제 토스 API 호출 없이 취소 처리 - cancelReason: {}, amount: {}",
                cancelReason,
                amount
            )
            return ResponseEntity.ok(TossCancelResponse("카드", amount, "0", tossPaymentKey, "test-order-id"))
        }

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

    companion object {
        /**
         * BaseInitData 가 만드는 시드 결제 키의 접두어. 이 키는 실제 토스 서버에 존재하지 않아서
         * 진짜 API 를 부르면 항상 404(NOT_FOUND_PAYMENT)가 난다 — 반복 테스트를 위해, 이 접두어로
         * 시작하는 키는 실제 토스 API 를 호출하지 않고 성공한 것처럼 처리한다. 진짜 결제 키(실제
         * 토스 결제창을 거쳐 발급된 키)는 이 접두어를 쓰지 않으므로 정상적으로 진짜 API 를 탄다.
         */
        private const val SEED_PAYMENT_KEY_PREFIX = "INIT-PAYKEY-"
    }
}
