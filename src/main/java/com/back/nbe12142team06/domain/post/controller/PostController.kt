package com.back.nbe12142team06.domain.post.controller

import com.back.nbe12142team06.domain.post.dto.PostDto
import com.back.nbe12142team06.domain.post.dto.PostSearchConditionDto
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.dto.PostWriteResponse
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(name = "공고", description = "동행 공고 등록, 조회, 수정, 삭제 및 상태 변경 관련 API")
@RestController
@RequestMapping("/api/v1/posts")
class PostController(
    private val postService: PostService,
    private val userRepository: UserRepository,
) {

    @Operation(
        summary = "공고 목록 조회",
        description = "검색 조건과 정렬 조건을 적용하여 공고 목록을 페이지 단위로 조회합니다.",
    )
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dateFrom: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dateTo: LocalDateTime?,
        @RequestParam(required = false) minPay: Int?,
        @RequestParam(required = false) maxPay: Int?,
        @RequestParam(defaultValue = "deadline") sort: String,
        // 모집중 탭(기본값) / 마감 탭 — 신규·모집중 공고가 마감 공고에 밀리지 않도록 목록 자체를 분리
        @RequestParam(defaultValue = "true") openOnly: Boolean,
    ): RsData<Page<PostDto>> {

        val sortOption = when (sort) {
            "payHigh" -> Sort.by(Sort.Direction.DESC, "hourlyPay")
            "payLow" -> Sort.by(Sort.Direction.ASC, "hourlyPay")
            "latest" -> Sort.by(Sort.Direction.DESC, "id")
            else -> Sort.by(Sort.Direction.ASC, "recruitEndAt") // 마감임박순 (기본값)
        }

        val condition = PostSearchConditionDto(
            keyword, region, dateFrom, dateTo, minPay, maxPay, openOnly,
        )

        // 목록은 환자 특이사항이 필요 없으므로 모두에게 가린다 (상세 조회에서만 권한에 따라 노출)
        val postDtoPage = postService.searchDtos(condition, PageRequest.of(page, size, sortOption))

        if (postDtoPage.isEmpty) {
            return RsData("200-12", "조회 내역이 없습니다.", postDtoPage)
        }
        return RsData("200-11", "목록 조회 성공", postDtoPage)
    }

    @Operation(
        summary = "내 공고 목록 조회",
        description = "로그인한 의뢰인이 작성한 공고 목록을 결제 상태와 무관하게 전부 조회합니다.",
    )
    @GetMapping("/me")
    fun myPosts(
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RsData<Page<PostDto>> {

        val postDtoPage = postService
            .findMyPosts(actor.id, PageRequest.of(page, size))
            .map { PostDto(it, true, true) }

        if (postDtoPage.isEmpty) {
            return RsData("200-14", "조회 내역이 없습니다.", postDtoPage)
        }
        return RsData("200-13", "내 공고 목록 조회 성공", postDtoPage)
    }

    @Operation(
        summary = "공고 상세 조회",
        description = "특정 공고의 상세 정보를 조회합니다.",
    )
    @GetMapping("/{postId}")
    fun detail(
        @PathVariable postId: Long,
        @AuthenticationPrincipal actor: SecurityUser,
    ): RsData<PostDto> {

        val post = postService.findById(postId)
        val canView = postService.canViewSensitive(post, actor.id)
        return RsData("200-15", "상세 조회 성공", PostDto(post, canView, post.client.id == actor.id))
    }

    @Operation(
        summary = "공고 등록",
        description = "의뢰인 또는 관리자가 새로운 동행 공고를 등록합니다.",
    )
    @PostMapping
    fun write(
        @AuthenticationPrincipal actor: SecurityUser,
        @RequestBody @Valid request: PostWriteRequest,
    ): RsData<PostWriteResponse> {

        val response = postService.write(actor.id, request)
        return RsData(
            "201-11",
            "${response.id}번 글이 성공적으로 등록되었습니다",
            response,
        )
    }

    @Operation(
        summary = "공고 수정",
        description = "의뢰인은 본인이 작성한 모집 중 공고를 모집 시작 전에 수정할 수 있으며, 관리자도 공고를 수정할 수 있습니다.",
    )
    @PutMapping("/{postId}")
    fun modify(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long,
        @RequestBody @Valid request: PostWriteRequest,
    ): RsData<PostDto> {
        postService.modify(postId, actor.id, request)

        return RsData<PostDto>(
            "200-16",
            "${postId}번 게시물이 수정되었습니다.",
        )
    }

    @Operation(
        summary = "공고 삭제",
        description = "의뢰인은 본인이 작성한 모집 중 또는 만료된 공고를 삭제할 수 있으며, 관리자도 공고를 삭제할 수 있습니다.",
    )
    @DeleteMapping("/{postId}")
    fun delete(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<PostDto> {

        postService.delete(postId, actor.id)

        return RsData<PostDto>(
            "204-11",
            "${postId}번 게시물이 삭제되었습니다.",
        )
    }

    @Operation(
        summary = "매칭 취소",
        description = "의뢰인은 본인의 매칭된 공고의 동행 매칭을 취소할 수 있으며, 관리자도 매칭을 취소할 수 있습니다.",
    )
    @PatchMapping("/{postId}/matchedCancel")
    fun matchedCancel(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<PostDto> {

        postService.matchedCancel(postId, actor.id)

        return RsData<PostDto>(
            "200-17",
            "${postId}번 게시물의 매칭이 취소되었습니다.",
        )
    }

    @Operation(
        summary = "동행 완료 처리",
        description = "진행 중인 동행을 완료 처리하고 실제 동행 시간을 반영합니다.",
    )
    @PatchMapping("/{postId}/escortComplete")
    fun escortComplete(
        @AuthenticationPrincipal actor: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<PostDto> {

        postService.escortComplete(postId, actor.id)

        return RsData<PostDto>(
            "200-18",
            "${postId}번 공고가 완료되었습니다. 실제 동행시간 및 지급액을 확인해주세요.",
        )
    }
}
