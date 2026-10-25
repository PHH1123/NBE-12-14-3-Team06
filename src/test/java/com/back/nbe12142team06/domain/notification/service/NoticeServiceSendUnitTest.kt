package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.event.NotificationEvent
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate

/**
 * NoticeService.send() 단위 테스트.
 *
 * 스프링 컨텍스트를 띄우지 않고 NoticeService 인스턴스를 직접 만들어 호출한다.
 * NoticeServiceTest(@SpringBootTest) 와 달리 프록시를 거치지 않는 것이 핵심이다 —
 * send() 는 @Async 라서 컨텍스트 안에서 부르면 다른 스레드로 넘어가 버려
 * 테스트가 저장·발행을 보기도 전에 끝난다. 여기서는 그냥 평범한 메서드 호출이다.
 *
 * send() 의 책임은 "알림을 저장하고 이벤트를 발행하는 것"까지다.
 * 실제 SSE 전송은 NotificationEventListener 가 커밋 후에 하므로 여기서는 다루지 않는다.
 */
class NoticeServiceSendUnitTest {

    private val notificationRepository: NotificationRepository = mock()
    private val emitterRepository: EmitterRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()
    private val userRepository: UserRepository = mock()

    private val noticeService = NoticeService(
        notificationRepository, emitterRepository, eventPublisher, userRepository
    )

    private val content = "동행매니저님이 공고에 지원하셨습니다."
    private val url = "/client/posts/1"

    private fun createClient(): User {
        val user = User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, 1L)
        return user
    }

    /** 저장된 뒤의 알림 — 실제 save() 가 돌려주는, 식별자가 붙은 엔티티를 흉내 낸다. */
    private fun savedNotification(receiver: User, content: String, url: String?): Notification =
        Notification(id = 1L, content = content, url = url, receiver = receiver)

    // send() 가 리포지토리·유저 조회를 거치도록 경계만 대역으로 바꾼다
    private fun stubSend(client: User, content: String, url: String?): Notification {
        val saved = savedNotification(client, content, url)
        doReturn(saved).whenever(notificationRepository).save(any())
        doReturn(client).whenever(userRepository).getReferenceById(any())
        return saved
    }

    @Test
    @DisplayName("[NoticeService.send] 받는 사람·내용·링크를 그대로 담아 알림을 저장한다")
    fun savesNotificationWithGivenArguments() {
        val client = createClient()
        stubSend(client, content, url)

        noticeService.send(client.id, content, url)

        val captor = argumentCaptor<Notification>()
        verify(notificationRepository).save(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.receiver).isSameAs(client)
        assertThat(saved.content).isEqualTo(content)
        assertThat(saved.url).isEqualTo(url)
    }

    @Test
    @DisplayName("[NoticeService.send] 새로 만든 알림은 읽지 않은 상태다")
    fun savesNotificationAsUnread() {
        val client = createClient()
        stubSend(client, "알림", null)

        noticeService.send(client.id, "알림", null)

        val captor = argumentCaptor<Notification>()
        verify(notificationRepository).save(captor.capture())
        assertThat(captor.firstValue.isRead).isFalse()
    }

    @Test
    @DisplayName("[NoticeService.send] 저장된 알림을 그대로 이벤트에 실어 발행한다")
    fun publishesEventCarryingSavedNotification() {
        val client = createClient()
        // 저장 전 알림에는 id 가 없다. 이벤트에는 id 가 붙은 "저장된" 쪽이 실려야
        // 리스너가 만드는 NotificationResponse 에 id 가 들어간다.
        val saved = stubSend(client, content, url)

        noticeService.send(client.id, content, url)

        val captor = argumentCaptor<NotificationEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        assertThat(event.notification).isSameAs(saved)
        assertThat(event.notification.id).isEqualTo(1L)
        assertThat(event.receiverId).isEqualTo(client.id)
        assertThat(event.content).isEqualTo(content)
        assertThat(event.url).isEqualTo(url)
    }

    @Test
    @DisplayName("[NoticeService.send] 링크가 없는 알림도 전송한다")
    fun sendsNotificationWithoutUrl() {
        val client = createClient()
        // 지금 ApplicationService.apply() 가 url 로 null 을 넘긴다.
        stubSend(client, content, null)

        noticeService.send(client.id, content, null)

        val notificationCaptor = argumentCaptor<Notification>()
        verify(notificationRepository).save(notificationCaptor.capture())
        assertThat(notificationCaptor.firstValue.url).isNull()

        val eventCaptor = argumentCaptor<NotificationEvent>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.url).isNull()
    }

    @Test
    @DisplayName("[NoticeService.send] 알림을 저장한 뒤에 이벤트를 발행한다")
    fun savesBeforePublishing() {
        val client = createClient()
        stubSend(client, "알림", null)

        noticeService.send(client.id, "알림", null)

        // 순서가 뒤집히면 리스너가 id 없는 알림을 받게 된다.
        inOrder(notificationRepository, eventPublisher) {
            verify(notificationRepository).save(any())
            verify(eventPublisher).publishEvent(any<NotificationEvent>())
        }
    }

    @Test
    @DisplayName("[NoticeService.send] SSE 전송은 리스너에 맡기고 직접 하지 않는다")
    fun doesNotPushToEmittersItself() {
        val client = createClient()
        stubSend(client, "알림", null)

        noticeService.send(client.id, "알림", null)

        // send() 가 직접 emitter 를 건드리면 커밋 전에 알림이 나가게 된다.
        verifyNoInteractions(emitterRepository)
    }
}
