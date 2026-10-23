package com.back.nbe12142team06.domain.notification.event;

import com.back.nbe12142team06.domain.notification.dto.NotificationResponse;
import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository;
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.response.RsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final EmitterRepository emitterRepository;

    @TransactionalEventListener
    public void sendEvent(NotificationEvent event) {
        Map<String, SseEmitter> emitterMap = emitterRepository.findAllByUserId(event.receiverId());
        RsData<NotificationResponse> rsData = new RsData<>("200-132", "알림이 전송되었습니다.",
                new NotificationResponse(event.notification()));

        emitterMap.forEach((key, emitter) ->
                sendToClient(emitter, key, rsData));
    }

    private void sendToClient(SseEmitter sseEmitter, String emitterId, Object data) {
        try {
            sseEmitter.send(event()
                    .id(emitterId)
                    .data(data)
                    .reconnectTime(3000L));
        } catch (IOException e) {
            sseEmitter.completeWithError(e);
            log.warn("SSE 전송 실패 emitterId: {}, data: {}", emitterId, data);
        }
    }
}
