package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoticeServiceTest {

    @field:Autowired private lateinit var noticeService: NoticeService
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var em: EntityManager

    @field:MockitoSpyBean private lateinit var notificationRepository: NotificationRepository

    // id 가 null 이면 저장할 때 DB 가 채우고, 값을 주면 저장 없이 리플렉션으로 넣는다
    private fun createClient(id: Long? = 1L): User = User(
        "client01", "password1!", "client01@email.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
    ).also { if (id != null) setId(it, id) }

    // 저장하지 않은 엔티티에도 id 가 필요해서 리플렉션으로 넣는다
    private fun setId(user: User, id: Long) {
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
    }

    private fun createNotification(receiver: User, content: String): Notification =
        Notification(content = content, receiver = receiver)

    @Test
    @DisplayName("[NoticeService] SSE 연결 성공")
    fun connect() {
        val client = createClient()

        val emitter = noticeService.connect(client.id)

        assertThat(emitter).isNotNull()
        assertThat(emitter.timeout).isEqualTo(300_000L)
    }

    @Test
    @DisplayName("[NoticeService] 알림 목록 가져오기")
    fun getPage() {
        val client = userRepository.save(createClient(id = null))
        notificationRepository.save(createNotification(client, "알림1"))
        notificationRepository.save(createNotification(client, "알림2"))

        val notifications = noticeService.getPage(
            client.id, PageRequest.of(0, 10, Sort.Direction.DESC, "id")
        ).content

        // 최신 알림이 먼저 오고, 조회만으로는 읽음 처리되지 않는다
        assertThat(notifications.map { it.content }).containsExactly("알림2", "알림1")
        assertThat(notifications).allSatisfy { assertThat(it.isRead).isFalse() }
    }

    @Test
    @DisplayName("[NoticeService] 알림 읽기 처리 - 페이지를 넘는 알림까지 모두 읽는다")
    fun readListReadsEveryNotification() {
        val client = userRepository.save(createClient(id = null))
        val other = userRepository.save(
            User(
                "client02", "password1!", "client02@email.com", "다른의뢰인",
                Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-5678", "서울"
            )
        )
        repeat(15) { notificationRepository.save(createNotification(client, "알림$it")) }
        val othersNotification = notificationRepository.save(createNotification(other, "남의 알림"))

        // 한 페이지(10건)만 요청해도 15건 전부 읽음 처리되어야 한다
        val firstPage = noticeService.readList(client.id, PageRequest.of(0, 10)).content
        em.flush()
        em.clear()

        assertThat(firstPage).hasSize(10)
        assertThat(notificationRepository.findByReceiverId(client.id, PageRequest.of(0, 20)).content)
            .hasSize(15)
            .allSatisfy { assertThat(it.isRead).isTrue() }

        // 다른 사용자의 알림은 건드리지 않는다
        assertThat(notificationRepository.findById(othersNotification.requiredId).get().isRead).isFalse()
    }

    @Test
    @DisplayName("[NoticeService] 단건 읽기")
    fun read() {
        val client = createClient()
        val notification = createNotification(client, "알림1")

        doReturn(notification).whenever(notificationRepository).findByIdAndDeletedAtIsNull(any())

        val readNotification = noticeService.read(client.id, 0L)

        assertThat(readNotification.content).isEqualTo("알림1")
        assertThat(readNotification.isRead).isTrue()
    }

    @Test
    @DisplayName("[NoticeService] 알림 제거")
    fun removeNotification() {
        val client = createClient()
        val notification = createNotification(client, "알림1")

        doReturn(notification).whenever(notificationRepository).findByIdAndDeletedAtIsNull(any())
        assertThat(notification.deletedAt).isNull()

        val removed = noticeService.removeNotification(client.id, 0L)

        assertThat(removed.deletedAt).isNotNull()
    }
}
