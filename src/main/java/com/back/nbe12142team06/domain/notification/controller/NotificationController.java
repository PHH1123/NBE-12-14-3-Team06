package com.back.nbe12142team06.domain.notification.controller;

import com.back.nbe12142team06.domain.notification.dto.NotificationResponse;
import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.global.response.RsData;
import com.back.nbe12142team06.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final NoticeService noticeService;

    @GetMapping(path = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public RsData<SseEmitter> connect(@AuthenticationPrincipal SecurityUser user) {
        Long userId = user.getId();
        SseEmitter emitter = noticeService.connect(userId);
        return new RsData<>("200-131", "SSE 연결되었습니다.", emitter);
    }

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

    @PatchMapping
    public RsData<?> read(@AuthenticationPrincipal SecurityUser user,
                          @RequestParam(required = false, defaultValue = "0") int page,
                          @RequestParam(required = false, defaultValue = "10") int size) {
        Long userId = user.getId();
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");
        Page<Notification> notifications = noticeService.read(userId, pageable);

        return new RsData<>("200-133", "알림을 읽음 처리했습니다.",
                notifications.map(NotificationResponse::new));
    }
}
