package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.event.NoticeRequestedEvent
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@Service
class NoticeService(
    private val notificationRepository: NotificationRepository,
    private val emitterRepository: EmitterRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun connect(userId: Long): SseEmitter {
        // 5분
        val emitterId = userId.toString() + "_" + System.currentTimeMillis()
        val sseEmitter = SseEmitter(300000L)
        emitterRepository.save(userId, emitterId, sseEmitter)

        // 연결 종료 시 인메모리 해시맵에서 제거 예약
        sseEmitter.onCompletion { emitterRepository.delete(userId, emitterId) }

        sseEmitter.onTimeout { sseEmitter.complete() }
        sseEmitter.onError { sseEmitter.complete() }

        // 연결 전송
        sendToClient(sseEmitter, emitterId, "connect")

        return sseEmitter
    }

    @Transactional
    fun send(receiverId: Long, content: String, url: String?) {
        eventPublisher.publishEvent(
            NoticeRequestedEvent(
                content, url, receiverId
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
            throw InvalidException(131, "SSE 전송에 실패했습니다.")
        }
    }

    @Transactional(readOnly = true)
    fun getPage(userId: Long, pageable: Pageable): Page<Notification> =
        notificationRepository.findByReceiverId(userId, pageable)

    @Transactional
    fun readList(userId: Long, pageable: Pageable): Page<Notification> {
        notificationRepository.readAllByUserId(userId)

        return notificationRepository.findByReceiverId(userId, pageable)
    }

    @Transactional
    fun read(userId: Long, notificationId: Long): Notification {
        val notification: Notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
            ?: throw NotFoundException(131, "알림을 찾을 수 없습니다.")

        if (notification.receiver.id != userId) {
            throw ForbiddenException(131, "알림을 읽을 권한이 없습니다.")
        }

        notification.read()

        return notification
    }

    @Transactional
    fun removeNotification(userId: Long, notificationId: Long): Notification {
        val notification: Notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
            ?: throw NotFoundException(131, "알림을 찾을 수 없습니다.")

        if (notification.receiver.id != userId) {
            throw ForbiddenException(132, "알림을 삭제할 권한이 없습니다.")
        }

        notification.delete()

        return notification
    }
}
