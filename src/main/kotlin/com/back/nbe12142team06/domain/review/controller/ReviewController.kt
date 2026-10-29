package com.back.nbe12142team06.domain.review.controller

import com.back.nbe12142team06.domain.review.dto.ReviewWriteRequest
import com.back.nbe12142team06.domain.review.dto.ReviewWriteResponse
import com.back.nbe12142team06.domain.review.service.ReviewService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "리뷰", description = "동행 리뷰 작성 및 조회 관련 API")
@RestController
@RequestMapping("/api/v1/applications")
class ReviewController(
    private val reviewService: ReviewService
) {

    @Operation(
        summary = "동행 리뷰 작성",
        description = "특정 동행 건에 대한 리뷰를 작성합니다.",
    )
    @PostMapping("/{applicationId}/reviews")
    fun write(
        @PathVariable("applicationId") applicationId: Long,
        @AuthenticationPrincipal actor: SecurityUser?,
        @RequestBody @Valid request: ReviewWriteRequest,
    ): RsData<ReviewWriteResponse> {
        val review = reviewService.write(applicationId, actor!!.id, request)

        return RsData(
            "201-61",
            "%d번 동행 건의 리뷰가 등록되었습니다.".format(applicationId),
            ReviewWriteResponse(review),
        )
    }
}
