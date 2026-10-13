package com.back.nbe12142team06.domain.notification.service;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.event.NotificationEvent;
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository;
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.global.exception.InvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

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
        sseEmitter.onCompletion(() -> emitterRepository.deleteAllEmitterStartWithUserId(userId));

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
//        Notification notification = Notification.builder()
//                .content(content)
//                .url(url)
//                .receiver(receiver)
//                .build();
//        Notification savedNotification = notificationRepository.save(notification);
//
//        Map<String, SseEmitter> emitterMap = emitterRepository.findAllEmitterStartWithByUserId(receiver.getId());
//
//        emitterMap.forEach((key, emitter) ->
//                sendToClient(emitter, key,
//                        new RsData<>("200-132", "알림이 전송되었습니다.",
//                        new NotificationResponse(savedNotification))));
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

    @Transactional
    public Page<Notification> getPage(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByReceiverId(userId, pageable);
        notifications.getContent().forEach(Notification::read);

        return notifications;
    }
}
