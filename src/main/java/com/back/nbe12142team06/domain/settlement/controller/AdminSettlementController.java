package com.back.nbe12142team06.domain.settlement.controller;

import com.back.nbe12142team06.domain.settlement.dto.SettlementAdminResponse;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.service.SettlementService;
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

@Tag(name = "관리자 정산", description = "관리자 정산 조회, 요청 및 정산 통계 조회 관련 API")
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    @Operation(
            summary = "기간별 정산 금액 및 건수 조회",
            description = "관리자가 기간별 정산 통계를 조회합니다."
    )
    @GetMapping("/analytics")
    public RsData<SettlementAdminResponse> getDailyAnalytics(@RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate) {

        List<Settlement> settlementList = settlementService.getAnalytics(startDate, endDate);

        return new RsData<>("200-161",
                "관리자 정산 통계 조회가 완료되었습니다.",
                new SettlementAdminResponse(settlementList));
    }
}
