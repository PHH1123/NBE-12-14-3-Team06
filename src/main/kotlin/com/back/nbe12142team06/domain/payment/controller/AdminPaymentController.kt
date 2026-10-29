package com.back.nbe12142team06.domain.payment.controller

import com.back.nbe12142team06.domain.payment.dto.PaymentAdminResponse
import com.back.nbe12142team06.domain.payment.dto.PaymentAnalyticsResponse
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.global.response.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "관리자 결제", description = "관리자 결제 조회, 수정 및 결제 통계 조회 관련 API")
@RestController
@RequestMapping("/api/v1/admin/payments")
class AdminPaymentController(
    private val paymentService: PaymentService
) {

    @Operation(
        summary = "일별 결제 통계 조회",
        description = "관리자가 일별 결제 통계를 조회합니다. 결제는 완료되거나 부분 취소된 결제만 조회합니다."
    )
    @GetMapping("/analytics/daily")
    fun getDailyAnalytics(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): RsData<List<PaymentDailyAnalyticsDto>> {
        val result: List<PaymentDailyAnalyticsDto> =
            paymentService.getDailyPaymentAnalytics(startDate, endDate)

        return RsData(
            "200-151", "관리자 일별 결제 통계 조회가 완료되었습니다.",
            result
        )
    }

    @Operation(
        summary = "기간별 결제 및 환불 금액 통계 조회",
        description = "관리자가 기간별 결제 및 환불 금액 통계를 조회합니다. 결제는 완료되거나 부분 취소된 결제만 조회합니다."
    )
    @GetMapping("/analytics")
    fun getAnalyticsByPeriod(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): RsData<PaymentAnalyticsResponse> {
        val result: List<Payment> =
            paymentService.getPaymentAnalyticsByPeriod(startDate, endDate)

        return RsData(
            "200-152", "관리자 기간별 결제 및 취소 환불 금액 통계 조회가 완료되었습니다.",
            PaymentAnalyticsResponse(result)
        )
    }

    @Operation(
        summary = "기간별 결제 목록 조회",
        description = "관리자가 기간별 결제 목록을 조회합니다."
    )
    @GetMapping
    fun getPaymentList(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): RsData<Page<PaymentAdminResponse>> {
        val pageable: Pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id")

        val result: Page<Payment> =
            paymentService.getPaymentsForAdmin(startDate, endDate, pageable)

        return RsData(
            "200-153", "관리자 기간별 결제 목록 조회가 완료되었습니다.",
            result.map { payment: Payment -> PaymentAdminResponse(payment) }
        )
    }

    @Operation(
        summary = "결제 상태 변경",
        description = "관리자가 결제 상태를 변경합니다."
    )
    @PatchMapping("/{paymentId}/status")
    fun updateStatus(
        @PathVariable paymentId: Long,
        @RequestParam("status") status: PaymentStatus
    ): RsData<Any> {
        paymentService.updateStatusForAdmin(paymentId, status)

        return RsData(
            "200-154",
            "관리자 결제 상태가 변경되었습니다."
        )
    }
}
