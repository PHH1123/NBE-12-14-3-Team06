package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileModifyRequest
import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileRequest
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest
@ActiveProfiles("test")
class EscortProfileConcurrencyTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    // username만 같고 email, phoneNum은 스레드마다 다르게
    private fun createRequest(username: String, i: Int) = UserSignUpRequest(
        username,
        "Password123!",
        "user$i@test.com",
        "테스트",
        Role.ESCORT,
        Gender.MALE,
        LocalDate.of(1990, 1, 1),
        "010%08d".format(i),  // 01000000000 ~ 01000000009
        "서울",
    )

    @Test
    @DisplayName("[EscortProfileConcurrencyTest] 교육 이수와 프로필 수정이 겹칠 때")
    fun t1() {
        val userId = userService.signUp(createRequest("user1", 0)).id

        val createEscortProfile = EscortProfileRequest(
            intro = "배 아파요. 너무 졸려요.",
            bankName = "귀가 은행",
            accountHolder = "최동행",
            accountNumber = "1111-1111-1111",
        )

        val updateEscortProfile = EscortProfileModifyRequest(
            intro = "가나다라마바사",
            bankName = "한글 은행",
            accountHolder = "박동행",
            accountNumber = "1111-1122-1111",
        )

        userService.createEscortProfile(userId, createEscortProfile)

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        val verifySucceeded = AtomicBoolean()
        val modifySucceeded = AtomicBoolean()
        val unexpected = Collections.synchronizedList(mutableListOf<Throwable>())

        val finished = Executors.newFixedThreadPool(2).use { executor ->
            // 두 작업을 동시에 출발시키고, 낙관적 락 충돌은 정상 실패로 본다
            fun race(succeeded: AtomicBoolean, action: () -> Unit) = executor.submit {
                ready.countDown()
                try {
                    start.await()
                    action()
                    succeeded.set(true)
                } catch (_: OptimisticLockingFailureException) {
                    // 충돌 시 한쪽 실패는 정상
                } catch (e: Throwable) {
                    unexpected.add(e)
                } finally {
                    done.countDown()
                }
            }

            // 교육 이수
            race(verifySucceeded) {
                transactionTemplate.executeWithoutResult {
                    val profile = escortProfileRepository.findByIdOrNull(userId)!!
                    escortProfileRepository.verify(profile.requiredId, LocalDateTime.now())
                } // 여기서 커밋 → @Version 비교
            }

            // 프로필 수정
            race(modifySucceeded) {
                userService.updateEscortProfile(userId, updateEscortProfile)
            }

            // 시작
            ready.await()
            start.countDown()
            done.await(10, TimeUnit.SECONDS)
        }

        val result = escortProfileRepository.findByIdOrNull(userId)!!

        assertThat(finished).`as`("제한 시간 내 종료").isTrue()
        assertThat(unexpected).`as`("예상하지 못한 예외").isEmpty()
        assertThat(verifySucceeded.get() || modifySucceeded.get()).`as`("최소 한쪽은 성공").isTrue()

        if (verifySucceeded.get()) {
            assertThat(result.verified).`as`("신원 인증 유지").isTrue()
        }
        if (modifySucceeded.get()) {
            assertThat(result.bankName).`as`("수정 내용 유지").isEqualTo("한글 은행")
            assertThat(result.accountNumber).isEqualTo("1111-1122-1111")
        }
    }
}
