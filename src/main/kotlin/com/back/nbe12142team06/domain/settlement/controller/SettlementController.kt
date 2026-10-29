package com.back.nbe12142team06.domain.settlement.controller

import com.back.nbe12142team06.domain.settlement.dto.SettlementResponse
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "정산", description = "동행 정산 요청 및 조회 관련 API")
@RestController
@RequestMapping("/api/v1/settlements")
class SettlementController(
    private val settlementService: SettlementService
) {

    @Operation(summary = "정산 요청", description = "특정 정산 건에 대해 정산을 요청합니다.")
    @PostMapping("/{settlementId}")
    fun settlementRequest(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable settlementId: Long
    ): RsData<Void> {
        val userId = actor.id
        settlementService.request(userId, settlementId)

        return RsData("200-71", "정산에 성공했습니다.")
    }

    @Operation(summary = "정산 목록 조회", description = "기간, 페이지 및 정렬 조건을 적용하여 현재 사용자의 정산 목록을 조회합니다.")
    @GetMapping
    fun settlementList(
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "DESC") sort: Sort.Direction
    ): RsData<Page<SettlementResponse>> = RsData(
        "200-72", "정산 목록을 가져왔습니다.",
        settlementService.findAll(
            userId = actor.id,
            startDate = startDate,
            endDate = endDate,
            pageable = PageRequest.of(
                page, size,
                Sort.by(sort, "settledDate").and(Sort.by(sort, "id"))
            )
        )
    )

    @Operation(summary = "정산 상세 조회", description = "특정 정산 건의 상세 정보를 조회합니다.")
    @GetMapping("/{settlementId}")
    fun settlementDetail(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable settlementId: Long
    ): RsData<SettlementResponse> = RsData(
        "200-73", "정산 상세 데이터를 조회했습니다.",
        settlementService.findSettlement(actor.id, settlementId)
    )

}
