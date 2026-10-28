package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
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
    ).also { entity -> id?.let { setId(entity, id) }}

    // 저장하지 않은 엔티티에도 id 가 필요해서 리플렉션으로 넣는다
    private fun setId(user: User, id: Long) {
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
    }

    private fun createOtherClient(): User = User(
        "client02", "password1!", "client02@email.com", "다른의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-5678", "서울"
    )

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
        val other = userRepository.save(createOtherClient())
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
        assertThat(notificationRepository.findByIdOrNull(othersNotification.requiredId)!!.isRead).isFalse()
    }

    @Test
    @DisplayName("[NoticeService] 알림 목록은 삭제한 알림과 남의 알림을 제외한다")
    fun getPageExcludesDeletedAndOthers() {
        val client = userRepository.save(createClient(id = null))
        val other = userRepository.save(createOtherClient())
        val mine = notificationRepository.save(createNotification(client, "내 알림"))
        val deleted = notificationRepository.save(createNotification(client, "삭제한 알림"))
        notificationRepository.save(createNotification(other, "남의 알림"))

        noticeService.removeNotification(client.id, deleted.requiredId)
        em.flush()
        em.clear()

        val notifications = noticeService.getPage(client.id, PageRequest.of(0, 10)).content

        assertThat(notifications.map { it.requiredId }).containsExactly(mine.requiredId)
    }

    @Test
    @DisplayName("[NoticeService] 단건 읽기")
    fun read() {
        val client = userRepository.save(createClient(id = null))
        val notification = notificationRepository.save(createNotification(client, "알림1"))

        val readNotification = noticeService.read(client.id, notification.requiredId)

        assertThat(readNotification.content).isEqualTo("알림1")
        assertThat(readNotification.isRead).isTrue()
    }

    @Test
    @DisplayName("[NoticeService] 남의 알림은 읽을 수 없다")
    fun readOthersNotification() {
        val client = userRepository.save(createClient(id = null))
        val other = userRepository.save(createOtherClient())
        val notification = notificationRepository.save(createNotification(other, "남의 알림"))

        assertThatThrownBy { noticeService.read(client.id, notification.requiredId) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessage("알림을 읽을 권한이 없습니다.")

        // 남의 알림은 읽음 처리도 되지 않는다
        em.flush()
        em.clear()
        assertThat(notificationRepository.findByIdOrNull(notification.requiredId)!!.isRead).isFalse()
    }

    @Test
    @DisplayName("[NoticeService] 없는 알림은 읽을 수 없다")
    fun readMissingNotification() {
        val client = userRepository.save(createClient(id = null))

        assertThatThrownBy { noticeService.read(client.id, 10_000L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("알림을 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("[NoticeService] 알림 제거")
    fun removeNotification() {
        val client = userRepository.save(createClient(id = null))
        val notification = notificationRepository.save(createNotification(client, "알림1"))
        assertThat(notification.deletedAt).isNull()

        val removed = noticeService.removeNotification(client.id, notification.requiredId)

        assertThat(removed.deletedAt).isNotNull()
    }

    @Test
    @DisplayName("[NoticeService] 삭제한 알림은 다시 찾을 수 없다")
    fun readDeletedNotification() {
        val client = userRepository.save(createClient(id = null))
        val notification = notificationRepository.save(createNotification(client, "알림1"))

        noticeService.removeNotification(client.id, notification.requiredId)
        em.flush()
        em.clear()

        // 소프트 딜리트된 알림은 읽기·삭제 모두 404 다
        assertThatThrownBy { noticeService.read(client.id, notification.requiredId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("알림을 찾을 수 없습니다.")
        assertThatThrownBy { noticeService.removeNotification(client.id, notification.requiredId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("알림을 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("[NoticeService] 남의 알림은 삭제할 수 없다")
    fun removeOthersNotification() {
        val client = userRepository.save(createClient(id = null))
        val other = userRepository.save(createOtherClient())
        val notification = notificationRepository.save(createNotification(other, "남의 알림"))

        assertThatThrownBy { noticeService.removeNotification(client.id, notification.requiredId) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessage("알림을 삭제할 권한이 없습니다.")

        em.flush()
        em.clear()
        assertThat(notificationRepository.findByIdOrNull(notification.requiredId)!!.deletedAt).isNull()
    }

    @Test
    @DisplayName("[NoticeService] 없는 알림은 삭제할 수 없다")
    fun removeMissingNotification() {
        val client = userRepository.save(createClient(id = null))

        assertThatThrownBy { noticeService.removeNotification(client.id, 10_000L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("알림을 찾을 수 없습니다.")
    }
}
