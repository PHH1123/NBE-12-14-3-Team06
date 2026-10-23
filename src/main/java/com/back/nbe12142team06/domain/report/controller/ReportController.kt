package com.back.nbe12142team06.domain.report.controller

import com.back.nbe12142team06.domain.report.dto.ReportDto
import com.back.nbe12142team06.domain.report.dto.ReportWriteRequest
import com.back.nbe12142team06.domain.report.service.ReportService
import com.back.nbe12142team06.domain.report.service.ReportSummaryService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "보고서", description = "동행 진료 보고서 작성 및 조회 관련 API")
@RestController
@RequestMapping("/api/v1/applications")
class ReportController(
    private val reportService: ReportService,
    private val reportSummaryService: ReportSummaryService,
) {

    @Operation(
        summary = "진료 보고서 작성",
        description = "특정 동행 건의 진료 보고서를 작성하고 저장된 내용을 기반으로 AI 요약을 생성합니다.",
    )
    @PostMapping("/{applicationId}/report")
    fun write(
        @PathVariable applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestBody @Valid request: ReportWriteRequest,
    ): RsData<ReportDto> {

        // 원문 저장 (트랜잭션 종료)
        val report = reportService.write(applicationId, actor.id, request)

        // AI 요약 (트랜잭션 밖에서 외부 API 호출)
        // 실패하더라도 원문은 이미 저장되어 있으므로 예외를 전파하지 않는다
        reportSummaryService.summarize(report.id!!)

        return RsData(
            "201-51",
            "${applicationId}번 동행 건의 보고서가 등록되었습니다.",
            reportService.findByApplicationId(applicationId, actor.id),
        )
    }

    @Operation(
        summary = "진료 보고서 조회",
        description = "특정 동행 건의 진료 보고서를 조회합니다.",
    )
    @GetMapping("/{applicationId}/report")
    fun detail(
        @PathVariable applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ReportDto> {

        val reportDto = reportService.findByApplicationId(applicationId, actor.id)

        return RsData("200-51", "보고서 조회 성공", reportDto)
    }
}
