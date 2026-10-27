package com.back.nbe12142team06.domain.payment.controller

import com.back.nbe12142team06.domain.payment.dto.PaymentCancelRequest
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmResponse
import com.back.nbe12142team06.domain.payment.dto.PaymentResponse
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "결제", description = "결제 승인, 검증, 조회 및 취소 관련 API")
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @Operation(
        summary = "결제 승인",
        description = "결제 요청 정보를 확인하고 결제를 승인합니다."
    )
    @PostMapping("/{paymentId}/confirm")
    fun requestConfirm(
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestBody request: PaymentConfirmRequest,
        @PathVariable paymentId: Long
    ): RsData<PaymentConfirmResponse> {
        val userId = actor.id

        paymentService.confirm(request, paymentId, userId)

        return RsData(
            "200-41", "결제 승인에 성공했습니다.",
            PaymentConfirmResponse(request)
        )
    }

    @Operation(
        summary = "결제 목록 조회",
        description = "현재 로그인한 사용자의 결제 내역을 조회합니다."
    )
    @GetMapping
    fun paymentList(@AuthenticationPrincipal actor: SecurityUser): RsData<List<PaymentResponse>> {
        val userId = actor.id

        val payments: List<Payment> = paymentService.findAll(userId)

        return RsData(
            "200-43", "결제 정보를 불러왔습니다.",
            payments.map { payment: Payment -> PaymentResponse(payment) })
    }

    @Operation(
        summary = "결제 상세 조회",
        description = "현재 로그인한 사용자의 특정 결제 정보를 조회합니다."
    )
    @GetMapping("/{paymentId}")
    fun getPayment(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable paymentId: Long
    ): RsData<PaymentResponse> {
        val userId = actor.id

        val payment: Payment = paymentService.findById(userId, paymentId)

        return RsData(
            "200-44", "결제 정보를 불러왔습니다.",
            PaymentResponse(payment)
        )
    }

    @Operation(
        summary = "결제 취소",
        description = "현재 로그인한 사용자의 특정 결제를 취소합니다."
    )
    @DeleteMapping("/{paymentId}")
    fun cancelPayment(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable paymentId: Long,
        @RequestBody request: PaymentCancelRequest
    ): RsData<Any> {
        val userId = actor.id

        paymentService.cancel(userId, paymentId, request)

        return RsData("204-41", "결제 취소 성공했습니다.")
    }

    @Operation(
        summary = "공고별 결제 조회",
        description = "현재 로그인한 사용자의 공고별 결제 정보를 조회합니다."
    )
    @GetMapping("/posts/{postId}")
    fun getPaymentByPost(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long
    ): RsData<PaymentResponse> {
        val userId = actor.id

        val payment = paymentService.findByPostIdAndReady(postId, userId)

        return RsData(
            "200-45", "결제 정보를 불러왔습니다.",
            PaymentResponse(payment)
        )
    }
}
