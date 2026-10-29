package com.back.nbe12142team06.domain.notification.event

import com.back.nbe12142team06.domain.notification.dto.NotificationResponse
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository
import com.back.nbe12142team06.global.response.RsData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@Component
class NotificationEventListener(
    private val emitterRepository: EmitterRepository
) {
    private val log = LoggerFactory.getLogger(NotificationEventListener::class.java)

    @TransactionalEventListener
    fun sendEvent(event: NotificationEvent) =
        emitterRepository.findAllByUserId(event.receiverId).forEach { (key: String, emitter: SseEmitter) ->
            sendToClient(
                emitter, key, RsData(
                    "200-132", "알림이 전송되었습니다.",
                    NotificationResponse(event.notification)
                )
            )
        }

    private fun sendToClient(sseEmitter: SseEmitter, emitterId: String, data: Any) {
        try {
            sseEmitter.send(
                SseEmitter.event()
                    .id(emitterId)
                    .data(data)
                    .reconnectTime(3000L)
            )
        } catch (e: IOException) {
            log.warn("SSE 전송 실패 emitterId: {}, data: {}", emitterId, data)
        }
    }
}
