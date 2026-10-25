package com.back.nbe12142team06.domain.notification.event

import com.back.nbe12142team06.domain.notification.entity.Notification
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
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate

/**
 * NoticeRequestedEventListener 단위 테스트.
 *
 * 스프링 컨텍스트를 띄우지 않고 리스너 인스턴스를 직접 만들어 호출한다.
 * 실제로는 @TransactionalEventListener(AFTER_COMMIT) + @Async 라서 본 작업 커밋 후
 * 다른 스레드에서 돌지만, 여기서는 그 안에서 하는 일 — 알림을 저장하고
 * SSE 전송용 이벤트를 발행하는 것 — 만 본다.
 * 트랜잭션 경계가 걸린 흐름은 NoticeServiceRollbackTest 에서 다룬다.
 */
class NoticeRequestedEventListenerTest {

    private val notificationRepository: NotificationRepository = mock()
    private val userRepository: UserRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()

    private val listener = NoticeRequestedEventListener(
        notificationRepository, userRepository, eventPublisher
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

    private fun stubSave(client: User, content: String, url: String?): Notification {
        val saved = savedNotification(client, content, url)
        doReturn(saved).whenever(notificationRepository).save(any())
        doReturn(client).whenever(userRepository).getReferenceById(any())
        return saved
    }

    private fun savedArgument(): Notification {
        val captor = argumentCaptor<Notification>()
        verify(notificationRepository).save(captor.capture())
        return captor.firstValue
    }

    private fun publishedEvent(): NotificationEvent {
        val captor = argumentCaptor<NotificationEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        return captor.firstValue
    }

    @Test
    @DisplayName("[NoticeRequestedEventListener] 받는 사람·내용·링크를 그대로 담아 알림을 저장한다")
    fun savesNotificationWithGivenArguments() {
        val client = createClient()
        stubSave(client, content, url)

        listener.on(NoticeRequestedEvent(content, url, client.id))

        val saved = savedArgument()
        assertThat(saved.receiver).isSameAs(client)
        assertThat(saved.content).isEqualTo(content)
        assertThat(saved.url).isEqualTo(url)
    }

    @Test
    @DisplayName("[NoticeRequestedEventListener] 새로 만든 알림은 읽지 않은 상태다")
    fun savesNotificationAsUnread() {
        val client = createClient()
        stubSave(client, "알림", null)

        listener.on(NoticeRequestedEvent("알림", null, client.id))

        assertThat(savedArgument().isRead).isFalse()
    }

    @Test
    @DisplayName("[NoticeRequestedEventListener] 저장된 알림을 그대로 이벤트에 실어 발행한다")
    fun publishesEventCarryingSavedNotification() {
        val client = createClient()
        // 저장 전 알림에는 id 가 없다. 이벤트에는 id 가 붙은 "저장된" 쪽이 실려야
        // NotificationEventListener 가 만드는 NotificationResponse 에 id 가 들어간다.
        val saved = stubSave(client, content, url)

        listener.on(NoticeRequestedEvent(content, url, client.id))

        val event = publishedEvent()
        assertThat(event.notification).isSameAs(saved)
        assertThat(event.notification.id).isEqualTo(1L)
        assertThat(event.receiverId).isEqualTo(client.id)
        assertThat(event.content).isEqualTo(content)
        assertThat(event.url).isEqualTo(url)
    }

    @Test
    @DisplayName("[NoticeRequestedEventListener] 링크가 없는 알림도 전송한다")
    fun sendsNotificationWithoutUrl() {
        val client = createClient()
        stubSave(client, content, null)

        listener.on(NoticeRequestedEvent(content, null, client.id))

        assertThat(savedArgument().url).isNull()
        assertThat(publishedEvent().url).isNull()
    }

    @Test
    @DisplayName("[NoticeRequestedEventListener] 알림을 저장한 뒤에 이벤트를 발행한다")
    fun savesBeforePublishing() {
        val client = createClient()
        stubSave(client, "알림", null)

        listener.on(NoticeRequestedEvent("알림", null, client.id))

        // 순서가 뒤집히면 SSE 리스너가 id 없는 알림을 받게 된다.
        inOrder(notificationRepository, eventPublisher) {
            verify(notificationRepository).save(any())
            verify(eventPublisher).publishEvent(any<NotificationEvent>())
        }
    }
}
