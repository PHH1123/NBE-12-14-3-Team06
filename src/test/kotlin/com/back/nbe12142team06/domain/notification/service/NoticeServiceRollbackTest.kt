package com.back.nbe12142team06.domain.notification.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * 알림 저장과 본 작업 트랜잭션의 관계를 확인한다.
 *
 * 규칙
 *  - 본 작업이 성공하면 알림이 저장된다. (알림 저장이 실패해도 본 작업은 성공해야 한다)
 *  - 본 작업이 롤백되면 알림은 저장되지 않아야 한다.
 *
 * send() 가 @Async 라서 다른 스레드에서 끝나므로, 커밋/롤백 후 잠시 기다리며 DB 를 확인한다.
 * 테스트 자체는 트랜잭션 없이 돌리고, 본 작업 트랜잭션은 TransactionTemplate 으로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NoticeServiceRollbackTest {

    private val content = "동행매니저님이 공고에 지원하셨습니다."

    @field:Autowired private lateinit var noticeService: NoticeService
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var transactionTemplate: TransactionTemplate
    @field:Autowired private lateinit var databaseCleaner: DatabaseCleaner

    @field:MockitoSpyBean private lateinit var notificationRepository: NotificationRepository

    private lateinit var receiver: User

    @BeforeEach
    fun setUp() {
        // 알림 받는 사람은 본 작업과 무관하게 미리 커밋해 둔다
        receiver = userRepository.save(
            User(
                "noticeReceiver", "password1!", "noticeReceiver@test.test", "의뢰인",
                Role.CLIENT, Gender.MALE, LocalDate.of(1990, 5, 20), "010-5555-0001", "서울시"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    // 알림이 저장되면 true. @Async 라서 최대 2초까지 기다린다.
    private fun notificationSaved(timeoutMillis: Long = 2_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (notificationRepository.findByReceiverId(receiver.id, PageRequest.of(0, 10))
                    .content.isNotEmpty()
            ) {
                return true
            }
            Thread.sleep(50)
        }
        return false
    }

    private fun saveBusinessData() {
        userRepository.save(
            User(
                "businessData", "password1!", "businessData@test.test", "본작업",
                Role.CLIENT, Gender.MALE, LocalDate.of(1990, 5, 20), "010-5555-0002", "서울시"
            )
        )
    }

    private fun businessDataExists(): Boolean = userRepository.findAll().any { it.username == "businessData" }

    @Test
    @DisplayName("[NoticeService.send] 본 작업이 커밋되면 알림이 저장된다")
    fun notificationSavedWhenBusinessCommits() {
        transactionTemplate.executeWithoutResult {
            saveBusinessData()
            noticeService.send(receiver.id, content, null)
        }

        assertThat(notificationSaved()).isTrue()
        assertThat(businessDataExists()).isTrue()
    }

    @Test
    @DisplayName("[NoticeService.send] 본 작업이 롤백되면 알림은 저장되지 않는다")
    fun notificationNotSavedWhenBusinessRollsBack() {
        assertThatCode {
            transactionTemplate.executeWithoutResult {
                saveBusinessData()
                noticeService.send(receiver.id, content, null)
                throw RuntimeException("본 작업 실패")
            }
        }.hasMessage("본 작업 실패")

        // 본 작업이 사라졌으면 알림도 남아 있으면 안 된다
        assertThat(businessDataExists()).isFalse()
        assertThat(notificationSaved()).isFalse()
    }

    @Test
    @DisplayName("[NoticeService.send] 알림 저장이 실패해도 본 작업은 성공한다")
    fun businessSucceedsWhenNotificationFails() {
        doThrow(RuntimeException("알림 저장 실패")).whenever(notificationRepository).save(any())

        assertThatCode {
            transactionTemplate.executeWithoutResult {
                saveBusinessData()
                noticeService.send(receiver.id, content, null)
            }
        }.doesNotThrowAnyException()

        assertThat(businessDataExists()).isTrue()
    }
}
