package com.back.nbe12142team06.domain.notification.service;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.event.NotificationEvent;
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository;
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.global.exception.ForbiddenException;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NotificationRepository notificationRepository;
    private final EmitterRepository emitterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SseEmitter connect(Long userId) {
        // 5분
        String emitterId = userId + "_" + System.currentTimeMillis();
        SseEmitter sseEmitter = new SseEmitter(300_000L);
        sseEmitter = emitterRepository.save(emitterId, sseEmitter);

        // 연결 전송
        sendToClient(sseEmitter, emitterId, "connect");

        // 연결 종료 시 인메모리 해시맵에서 제거 예약
        sseEmitter.onCompletion(() -> emitterRepository.deleteEmitterStartWithUserId(userId));

        return sseEmitter;
    }

    @Async
    @Transactional
    public void send(User receiver, String content, String url) {
        Notification notification = Notification.builder()
                .content(content)
                .url(url)
                .receiver(receiver)
                .build();
        Notification savedNotification = notificationRepository.save(notification);

        eventPublisher.publishEvent(new NotificationEvent(content, url, receiver, savedNotification));
    }

    private void sendToClient(SseEmitter sseEmitter, String emitterId, Object data) {
        try {
            sseEmitter.send(event()
                    .id(emitterId)
                    .data(data)
                    .reconnectTime(3000L));
        } catch (IOException e) {
            throw new InvalidException(131, "SSE 전송에 실패했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> getPage(Long userId, Pageable pageable) {
        return notificationRepository.findByReceiverId(userId, pageable);
    }

    @Transactional
    public Page<Notification> readList(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByReceiverId(userId, pageable);
        notifications.getContent().forEach(Notification::read);
        return notifications;
    }

    @Transactional
    public Notification read(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException(131, "알림을 찾을 수 없습니다."));

        if (!notification.getReceiver().getId().equals(userId)) {
            throw new ForbiddenException(131, "알림을 읽을 권한이 없습니다");
        }

        notification.read();

        return notification;
    }
}
