package com.back.nbe12142team06.domain.application.controller

import com.back.nbe12142team06.domain.application.dto.ApplicationAcceptResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationApplyResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationClientProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationEscortProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationListResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationProgressResponse
import com.back.nbe12142team06.domain.application.dto.EscortProgressRequest
import com.back.nbe12142team06.domain.application.dto.MyApplicationResponse
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "지원", description = "동행 지원 관련 API")
@RestController
@RequestMapping("/api/v1/applications")
open class ApplicationController(
    private val applicationService: ApplicationService,
) {
    @Operation(summary = "동행 지원", description = "특정 공고에 동행 지원을 신청합니다.")
    @PostMapping("/{postId}")
    open fun apply(
        @PathVariable("postId") postId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationApplyResponse> {
        val response = applicationService.apply(postId, actor.id)
        return RsData("201-21", "지원이 완료되었습니다.", response)
    }

    @Operation(summary = "지원 목록 조회", description = "특정 공고에 지원한 동행자 목록을 조회합니다.")
    @GetMapping("/posts/{postId}")
    open fun list(
        @PathVariable("postId") postId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "10") size: Int,
    ): RsData<Page<ApplicationListResponse>> {
        val responses = applicationService.list(postId, actor.id, PageRequest.of(page, size))
        return RsData("200-21", "지원 목록 조회가 완료되었습니다.", responses)
    }

    @Operation(summary = "지원 승인", description = "특정 동행자의 지원을 승인합니다.")
    @PatchMapping("/{applicationId}/accept")
    open fun accept(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationAcceptResponse> {
        val response = applicationService.accept(applicationId, actor.id)
        return RsData("200-22", "지원 승인이 완료되었습니다.", response)
    }

    @Operation(summary = "지원 거절", description = "특정 동행자의 지원을 거절합니다.")
    @PatchMapping("/{applicationId}/reject")
    open fun reject(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<Void> {
        applicationService.reject(applicationId, actor.id)
        return RsData("200-23", "지원 거절이 완료되었습니다.")
    }

    @Operation(summary = "지원 취소", description = "동행자 본인이 지원을 취소합니다.")
    @PatchMapping("/{applicationId}/cancel")
    open fun cancel(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<Void> {
        applicationService.cancel(applicationId, actor.id)
        return RsData("200-24", "지원 취소가 완료되었습니다.")
    }

    @Operation(summary = "동행 진행 상태 변경", description = "승인된 동행의 진행 상태를 변경합니다.")
    @PatchMapping("/{applicationId}/progress")
    open fun updateProgress(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
        @Valid @RequestBody request: EscortProgressRequest,
    ): RsData<Void> {
        applicationService.updateProgress(applicationId, actor.id, request.progress!!)
        return RsData("200-25", "동행 진행 상태가 변경되었습니다.")
    }

    @Operation(summary = "동행 진행 상태 조회", description = "승인된 동행의 현재 진행 단계를 조회합니다. 동행 매니저 본인 또는 그 공고를 작성한 의뢰인만 볼 수 있습니다.")
    @GetMapping("/{applicationId}/progress")
    open fun getProgress(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationProgressResponse> {
        val response = applicationService.getProgress(applicationId, actor.id)
        return RsData("200-26", "동행 진행 상태 조회가 완료되었습니다.", response)
    }

    @Operation(summary = "동행자 프로필 조회", description = "특정 지원의 동행자 프로필 정보를 조회합니다.")
    @GetMapping("/{applicationId}/escort-profile")
    open fun escortProfile(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationEscortProfileResponse> {
        val response = applicationService.getEscortProfile(applicationId, actor.id)
        return RsData("200-27", "지원자 프로필 조회가 완료되었습니다.", response)
    }

    @Operation(summary = "의뢰인 프로필 조회", description = "매칭된 의뢰인 프로필 정보를 조회합니다.")
    @GetMapping("/{applicationId}/client-profile")
    open fun clientProfile(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationClientProfileResponse> {
        val response = applicationService.getClientProfile(applicationId, actor.id)
        return RsData("200-28", "매칭된 의뢰인 프로필 조회가 완료되었습니다.", response)
    }

    @Operation(summary = "내 지원 목록 조회", description = "로그인한 동행 매니저의 지원 내역을 조회합니다.")
    @GetMapping("/me")
    open fun myApplications(
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<List<MyApplicationResponse>> {
        val responses = applicationService.getMyApplications(actor.id)
        return RsData("200-29", "내 지원 목록 조회가 완료되었습니다.", responses)
    }

    @Operation(summary = "동행 실시간 위치 전송", description = "동행 중인 동행 매니저가 현재 위치를 보냅니다. 가장 최근 위치 1건만 저장됩니다.")
    @PostMapping("/{applicationId}/location")
    open fun saveLocation(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
        @Valid @RequestBody request: ApplicationLocationRequest,
    ): RsData<Void> {
        applicationService.saveLocation(applicationId, actor.id, request)
        return RsData("200-30", "실시간 위치가 저장되었습니다.")
    }

    @Operation(summary = "동행 실시간 위치 조회", description = "동행 진행 중에만 최근 위치를 조회합니다. 해당 동행 매니저 본인과 공고 작성 의뢰인만 볼 수 있으며, 귀가 완료 시 위치정보가 삭제됩니다.")
    @GetMapping("/{applicationId}/location")
    open fun getLocation(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<ApplicationLocationResponse> {
        val response = applicationService.getLocation(applicationId, actor.id)
        return RsData("200-31", "실시간 위치 조회가 완료되었습니다.", response)
    }
}
