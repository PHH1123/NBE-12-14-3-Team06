package com.back.nbe12142team06.domain.settlement.controller;

import com.back.nbe12142team06.domain.settlement.dto.SettlementAdminResponse;
import com.back.nbe12142team06.domain.settlement.dto.SettlementAnalyticsResponse;
import com.back.nbe12142team06.domain.settlement.dto.SettlementResponse;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.service.SettlementService;
import com.back.nbe12142team06.global.response.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public RsData<SettlementAnalyticsResponse> getAnalytics(@RequestParam(required = false) LocalDate startDate,
                                                            @RequestParam(required = false) LocalDate endDate) {

        List<Settlement> settlementList = settlementService.getAnalytics(startDate, endDate);

        return new RsData<>("200-161",
                "관리자 정산 통계 조회가 완료되었습니다.",
                new SettlementAnalyticsResponse(settlementList));
    }

    @Operation(
            summary = "정산 목록 조회",
            description = "관리자가 정산 목록을 조회합니다."
    )
    @GetMapping
    public RsData<Page<SettlementAdminResponse>> getSettlements(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                @RequestParam(required = false) LocalDate startDate,
                                                                @RequestParam(required = false) LocalDate endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");

        Page<Settlement> settlementPage = settlementService.getSettlementForAdmin(startDate, endDate, pageable);

        return new RsData<>("200-162",
                "관리자 정산 목록 조회가 완료되었습니다.",
                settlementPage.map(SettlementAdminResponse::new));
    }
}
