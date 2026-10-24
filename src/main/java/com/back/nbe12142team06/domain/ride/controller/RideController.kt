package com.back.nbe12142team06.domain.ride.controller

import com.back.nbe12142team06.domain.ride.dto.RideResponse
import com.back.nbe12142team06.domain.ride.dto.RideResponse.Companion.from
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "이동수단", description = "동행 이동수단 조회 및 변경 관련 API")
@RestController
@RequestMapping("/api/v1/rides")
class RideController(
    private val rideService: RideService
) {

    @Operation(summary = "공고별 이동수단 목록 조회", description = "특정 공고에 등록된 이동수단 목록을 조회합니다.")
    @GetMapping("/posts/{postId}")
    fun getListByPostId(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long
    ): RsData<List<RideResponse>> = RsData(
        statusCode = "200-81",
        msg = "해당 공고의 이동 정보를 불러왔습니다.",
        data = rideService.getListByPostIdAndUserId(postId, actor.id)
    )

    @Operation(summary = "이동수단 상세 조회", description = "특정 이동수단의 상세 정보를 조회합니다.")
    @GetMapping("/{rideId}")
    fun getRide(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable rideId: Long
    ): RsData<RideResponse> = RsData(
        statusCode = "200-82",
        msg = "이동 정보를 불러왔습니다.",
        data = from(rideService.findById(actor.id, rideId))
    )
}
