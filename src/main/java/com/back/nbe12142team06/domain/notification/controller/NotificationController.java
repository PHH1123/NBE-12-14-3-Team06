package com.back.nbe12142team06.domain.notification.controller;

import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.global.response.RsData;
import com.back.nbe12142team06.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final NoticeService noticeService;

    @GetMapping(name = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public RsData<?> connect(@AuthenticationPrincipal SecurityUser user) {
        Long userId = user.getId();
        SseEmitter emitter = noticeService.connect(userId);
        return new RsData<>("200-131", "SSE 연결되었습니다.");
    }
}
