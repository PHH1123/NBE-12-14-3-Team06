package com.back.nbe12142team06.domain.notification.event

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NoticeRequestedEventListener(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: NoticeRequestedEvent) {
        val notification = notificationRepository.save(
            Notification(
                content = event.content,
                url = event.url,
                receiver = userRepository.getReferenceById(event.receiverId)
            )
        )
        eventPublisher.publishEvent(
            NotificationEvent(
                content = event.content,
                url = event.url,
                receiverId = event.receiverId,
                notification = notification
            )
        )
    }
}