package com.back.nbe12142team06.domain.notification.controller

import com.back.nbe12142team06.domain.notification.dto.NotificationResponse
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "알림", description = "알림 전송, 조회, 읽기 관련 API")
@RestController
@RequestMapping("/api/v1/notification")
class NotificationController(
    private val noticeService: NoticeService
) {

    @Operation(summary = "SSE 연결", description = "알림을 받기 위한 SSE 연결을 합니다.")
    @GetMapping(path = ["/connect"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun connect(@AuthenticationPrincipal user: SecurityUser): ResponseEntity<SseEmitter> =
        ResponseEntity.ok(noticeService.connect(user.id))

    @Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 조회합니다.")
    @GetMapping
    fun getPage(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int
    ): RsData<Page<NotificationResponse>> = RsData(
        "200-132", "알림 목록을 조회했습니다.",
        noticeService.getPage(
            userId = user.id,
            pageable = PageRequest.of(
                page, size, Sort.Direction.DESC, "id"
            )
        ).map { NotificationResponse(it) }
    )

    @Operation(summary = "모든 알림 읽음 처리", description = "모든 알림을 읽음 처리합니다.")
    @PatchMapping("/read")
    fun readList(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int
    ): RsData<Page<NotificationResponse>> =
        RsData(
            "200-133", "알림을 읽음 처리했습니다.",
            noticeService.readList(
                userId = user.id,
                pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id")
            ).map { NotificationResponse(it) }
        )

    @Operation(summary = "알림 읽음 처리", description = "알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}/read")
    fun read(
        @AuthenticationPrincipal user: SecurityUser,
        @PathVariable notificationId: Long
    ): RsData<NotificationResponse> =
        RsData(
            "200-133", "알림을 읽음 처리했습니다.",
            NotificationResponse(
                noticeService.read(
                    userId = user.id,
                    notificationId = notificationId
                )
            )
        )

    @Operation(summary = "알림 삭제 처리", description = "알림을 삭제 처리합니다.")
    @DeleteMapping("/{notificationId}")
    fun removeNotification(
        @AuthenticationPrincipal user: SecurityUser,
        @PathVariable notificationId: Long
    ): RsData<Void> {
        noticeService.removeNotification(
            userId = user.id,
            notificationId = notificationId
        )

        return RsData("204-131", "알림을 삭제했습니다.")
    }
}
