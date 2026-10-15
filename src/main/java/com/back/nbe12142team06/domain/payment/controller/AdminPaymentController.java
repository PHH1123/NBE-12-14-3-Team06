package com.back.nbe12142team06.domain.payment.controller;

import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto;
import com.back.nbe12142team06.domain.payment.service.PaymentService;
import com.back.nbe12142team06.global.response.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping("/daily")
    public RsData<?> getDailyAnalytics(@RequestParam(required = false) LocalDate startDate,
                                       @RequestParam(required = false) LocalDate endDate) {

        List<PaymentDailyAnalyticsDto> result =
                paymentService.getDailyPaymentAnalytics(startDate, endDate);

        return new RsData<>("200-201", "조회 완료",
                result);
    }
}
