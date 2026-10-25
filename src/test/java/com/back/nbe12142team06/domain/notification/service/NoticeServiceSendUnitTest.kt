package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.domain.notification.event.NoticeRequestedEvent
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher

/**
 * NoticeService.send() 단위 테스트.
 *
 * send() 의 책임은 "알림 요청 이벤트를 발행하는 것"뿐이다.
 * 알림 저장과 SSE 전송은 커밋 이후에 각각 NoticeRequestedEventListener,
 * NotificationEventListener 가 맡는다 — 본 작업이 롤백되면 알림이 남지 않아야 하므로
 * 여기서는 DB 를 건드리지 않는 것이 핵심이다. (NoticeServiceRollbackTest 참고)
 */
class NoticeServiceSendUnitTest {

    private val notificationRepository: NotificationRepository = mock()
    private val emitterRepository: EmitterRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()

    private val noticeService = NoticeService(
        notificationRepository, emitterRepository, eventPublisher
    )

    private val receiverId = 1L
    private val content = "동행매니저님이 공고에 지원하셨습니다."
    private val url = "/client/posts/1"

    private fun publishedEvent(): NoticeRequestedEvent {
        val captor = argumentCaptor<NoticeRequestedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        return captor.firstValue
    }

    @Test
    @DisplayName("[NoticeService.send] 받는 사람·내용·링크를 그대로 담아 알림 요청을 발행한다")
    fun publishesNoticeRequestedEvent() {
        noticeService.send(receiverId, content, url)

        val event = publishedEvent()
        assertThat(event.receiverId).isEqualTo(receiverId)
        assertThat(event.content).isEqualTo(content)
        assertThat(event.url).isEqualTo(url)
    }

    @Test
    @DisplayName("[NoticeService.send] 링크가 없는 알림도 요청할 수 있다")
    fun publishesEventWithoutUrl() {
        // 지금 ApplicationService.apply() 가 url 로 null 을 넘긴다.
        noticeService.send(receiverId, content, null)

        assertThat(publishedEvent().url).isNull()
    }

    @Test
    @DisplayName("[NoticeService.send] 호출자 트랜잭션에서는 알림을 저장하지 않는다")
    fun doesNotTouchDatabaseItself() {
        noticeService.send(receiverId, content, url)

        // 여기서 저장하면 본 작업이 롤백돼도 알림이 남는다
        verifyNoInteractions(notificationRepository)
    }

    @Test
    @DisplayName("[NoticeService.send] SSE 전송은 리스너에 맡기고 직접 하지 않는다")
    fun doesNotPushToEmittersItself() {
        noticeService.send(receiverId, content, url)

        // send() 가 직접 emitter 를 건드리면 커밋 전에 알림이 나가게 된다.
        verifyNoInteractions(emitterRepository)
    }
}
