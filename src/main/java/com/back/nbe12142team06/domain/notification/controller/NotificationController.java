package com.back.nbe12142team06.domain.notification.controller;

import com.back.nbe12142team06.domain.notification.dto.NotificationResponse;
import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.global.response.RsData;
import com.back.nbe12142team06.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "알림", description = "알림 전송, 조회, 읽기 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final NoticeService noticeService;

    @Operation(
            summary = "SSE 연결",
            description = "알림을 받기 위한 SSE 연결을 합니다."
    )
    @GetMapping(path = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connect(@AuthenticationPrincipal SecurityUser user) {
        Long userId = user.getId();
        SseEmitter emitter = noticeService.connect(userId);
        return ResponseEntity.ok(emitter);
    }

    @Operation(
            summary = "알림 목록 조회",
            description = "사용자의 알림 목록을 조회합니다."
    )
    @GetMapping
    public RsData<Page<NotificationResponse>> getPage(@AuthenticationPrincipal SecurityUser user,
                                                      @RequestParam(required = false, defaultValue = "0") int page,
                                                      @RequestParam(required = false, defaultValue = "10") int size) {

        Long userId = user.getId();
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");

        Page<Notification> notifications = noticeService.getPage(userId, pageable);

        return new RsData<>("200-132", "알림 목록을 조회했습니다.",
                notifications.map(NotificationResponse::new));
    }

    @Operation(
            summary = "모든 알림 읽음 처리",
            description = "모든 알림을 읽음 처리합니다."
    )
    @PatchMapping("/read")
    public RsData<Page<NotificationResponse>> readList(@AuthenticationPrincipal SecurityUser user,
                          @RequestParam(required = false, defaultValue = "0") int page,
                          @RequestParam(required = false, defaultValue = "10") int size) {
        Long userId = user.getId();
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");
        Page<Notification> notifications = noticeService.readList(userId, pageable);

        return new RsData<>("200-133", "알림을 읽음 처리했습니다.",
                notifications.map(NotificationResponse::new));
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "알림을 읽음 처리합니다."
    )
    @PatchMapping("/{notificationId}/read")
    public RsData<NotificationResponse> read(@AuthenticationPrincipal SecurityUser user,
                          @PathVariable Long notificationId) {
        Long userId = user.getId();
        Notification notification = noticeService.read(userId, notificationId);

        return new RsData<>("200-133", "알림을 읽음 처리했습니다.",
                new NotificationResponse(notification));
    }

    @Operation(
            summary = "알림 삭제 처리",
            description = "알림을 삭제 처리합니다."
    )
    @DeleteMapping("/{notificationId}")
    public RsData<?> removeNotification(@AuthenticationPrincipal SecurityUser user,
                                        @PathVariable Long notificationId) {
        Long userId = user.getId();
        noticeService.removeNotification(userId, notificationId);

        return new RsData<>("204-131", "알림을 삭제했습니다.");
    }
}
