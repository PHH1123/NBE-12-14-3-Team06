package com.back.nbe12142team06.domain.payment.controller;

import com.back.nbe12142team06.domain.payment.dto.PaymentAdminResponse;
import com.back.nbe12142team06.domain.payment.dto.PaymentAnalyticsResponse;
import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto;
import com.back.nbe12142team06.domain.payment.service.PaymentService;
import com.back.nbe12142team06.global.response.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "관리자 결제", description = "관리자 결제 조회, 수정 및 결제 통계 조회 관련 API")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "일별 결제 통계 조회",
            description = "관리자가 일별 결제 통계를 조회합니다. 결제는 완료되거나 부분 취소된 결제만 조회합니다."
    )
    @GetMapping("/analytics/daily")
    public RsData<List<PaymentDailyAnalyticsDto>> getDailyAnalytics(@RequestParam(required = false) LocalDate startDate,
                                                                    @RequestParam(required = false) LocalDate endDate) {

        List<PaymentDailyAnalyticsDto> result =
                paymentService.getDailyPaymentAnalytics(startDate, endDate);

        return new RsData<>("200-151", "관리자 일별 결제 통계를 조회가 완료되었습니다.",
                result);
    }

    @Operation(
            summary = "기간별 결제 및 환불 금액 통계 조회",
            description = "관리자가 기간별 결제 및 환불 금액 통계를 조회합니다. 결제는 완료되거나 부분 취소된 결제만 조회합니다."
    )
    @GetMapping("/analytics")
    public RsData<PaymentAnalyticsResponse> getAnalyticsByPeriod(@RequestParam(required = false) LocalDate startDate,
                                                                 @RequestParam(required = false) LocalDate endDate) {

        List<Payment> result =
                paymentService.getPaymentAnalyticsByPeriod(startDate, endDate);

        return new RsData<>("200-152", "관리자 기간별 결제 및 취소 환불 금액 통계 조회가 완료되었습니다.",
                new PaymentAnalyticsResponse(result));
    }

    @Operation(
            summary = "기간별 결제 목록 조회",
            description = "관리자가 기간별 결제 목록을 조회합니다."
    )
    @GetMapping
    public RsData<Page<PaymentAdminResponse>> getPaymentList(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");

        Page<Payment> result =
                paymentService.getPaymentsForAdmin(startDate, endDate, pageable);

        return new RsData<>("200-153", "관리자 기간별 결제 목록 조회가 완료되었습니다.",
                result.map(PaymentAdminResponse::new));
    }

    @Operation(
            summary = "결제 상태 변경",
            description = "관리자가 결제 상태를 변경합니다."
    )
    @PatchMapping("/{paymentId}/status")
    public RsData<?> updateStatus(@PathVariable Long paymentId,
                                  @RequestParam("status") PaymentStatus status) {

        paymentService.updateStatusForAdmin(paymentId, status);

        return new RsData<>("200-154",
                "관리자 결제 상태가 변경되었습니다.");
    }
}
