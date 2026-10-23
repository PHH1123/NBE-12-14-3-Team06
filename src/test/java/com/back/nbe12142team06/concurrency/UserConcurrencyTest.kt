package com.back.nbe12142team06.concurrency

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.dto.user.UserProfileUpdateRequest
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.exception.DuplicatedException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class UserConcurrencyTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

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
    private fun createRequest(username: String, i: Int): UserSignUpRequest {
        return UserSignUpRequest(
            username,
            "Password123!",
            "user$i@test.com",
            "테스트",
            Role.CLIENT,
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "010" + "%08d".format(i),   // 01000000000 ~ 01000000009
            "서울",
        )
    }

    @Test
    @DisplayName("[UserConcurrencyTest] 회원가입 - 같은 username으로 동시에 가입하면 1명만 가입되고 나머지는 DuplicatedException으로 거절")
    fun t1() {
        // given
        val username = "concurrent_user"

        val ready = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREAD_COUNT)

        val success = AtomicInteger()
        val duplicated = AtomicInteger()
        val unexpected: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

        // when
        val finished = Executors.newFixedThreadPool(THREAD_COUNT).use { executor ->
            for (i in 0 until THREAD_COUNT) {
                val request = createRequest(username, i)
                executor.submit(
                    Runnable {
                        ready.countDown()
                        try {
                            start.await()
                            userService.signUp(request)
                            success.incrementAndGet()
                        } catch (e: DuplicatedException) {
                            // email, phoneNum은 스레드마다 다르므로 중복 예외는 username 중복뿐
                            duplicated.incrementAndGet()
                        } catch (e: Throwable) {
                            unexpected.add(e)
                        } finally {
                            done.countDown()
                        }
                    },
                )
            }

            ready.await()
            start.countDown()
            done.await(10, TimeUnit.SECONDS)
        }

        assertThat(finished).isTrue()
        assertThat(unexpected).`as`("예상하지 못한 예외").isEmpty()  // 10개 중 0개의 예상치 못한 예외
        assertThat(success.get()).isEqualTo(1)     // 전체 요청 중 1개 성공
        assertThat(duplicated.get()).isEqualTo(THREAD_COUNT - 1)   // 전체 요청 중 1개를 제외히고는 전부 중복 예외
        assertThat(userRepository.count()).isEqualTo(1L)    // 실제 저장된 데이터는 1개의 행 뿐
    }

    @Test
    @DisplayName("[UserConcurrencyTest] 탈퇴와 수정 - 탈퇴 요청과 수정 요청이 동시에 실행돼도 탈퇴 시 마스킹 유지")
    fun t2() {
        val userId = userService.signUp(createRequest("user1", 0)).id

        val updateRequest = UserProfileUpdateRequest(
            "NewPassword123!",
            "new@test.com",
            "새이름",
            LocalDate.of(1991, 1, 1),
            "01099999999",
            "부산",
        )

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)

        val withdrawSucceeded = AtomicBoolean(false)
        val unexpected: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

        val finished = Executors.newFixedThreadPool(2).use { executor ->
            // 탈퇴
            executor.submit(
                Runnable {
                    ready.countDown()
                    try {
                        start.await()
                        userService.deleteMyProfile(userId)
                        withdrawSucceeded.set(true)
                    } catch (e: OptimisticLockingFailureException) {
                        // 수정이 먼저 커밋되어 충돌로 거절은 허용
                    } catch (e: Throwable) {
                        unexpected.add(e)
                    } finally {
                        done.countDown()
                    }
                },
            )

            // 회원 수정
            executor.submit(
                Runnable {
                    ready.countDown()
                    try {
                        start.await()
                        userService.updateMyProfile(userId, updateRequest)
                    } catch (e: OptimisticLockingFailureException) {
                        // 탈퇴가 먼저 커밋되어 충돌로 거절
                    } catch (e: UnauthorizedException) {
                        // 이미 탈퇴된 뒤 조회는 허용
                    } catch (e: Throwable) {
                        unexpected.add(e)
                    } finally {
                        done.countDown()
                    }
                },
            )

            ready.await()
            start.countDown()
            done.await(10, TimeUnit.SECONDS)
        }

        assertThat(finished).`as`("제한 시간 내 종료").isTrue()

        assertThat(unexpected).`as`("예상하지 못한 예외").isEmpty()

        val result = userRepository.findByIdIncludingDeleted(userId)
            .orElseThrow()
        val masked = "deleted_$userId"

        if (withdrawSucceeded.get()) {
            assertThat(result.isDeleted).`as`("탈퇴 상태 유지").isTrue()
            assertThat(result.username).`as`("username 마스킹 유지").isEqualTo(masked)
            assertThat(result.email).`as`("email 마스킹 유지").isEqualTo(masked)
            assertThat(result.phoneNum).`as`("phoneNum 마스킹 유지").isEqualTo(masked)
        } else {
            assertThat(result.isDeleted).`as`("탈퇴 실패 시 회원 유지").isFalse()
            assertThat(result.email).`as`("수정 반영").isEqualTo("new@test.com")
        }
    }

    @Test
    @DisplayName("[UserConcurrencyTest] 관리자 탈퇴 - 같은 회원을 동시에 탈퇴시키면 1건만 성공하고 나머지는 이미 탈퇴한 회원으로 거절")
    fun t3() {
        val userId = userService.signUp(createRequest("user1", 0)).id
        // deleteUser 는 adminId 를 자기 자신 탈퇴 여부 비교에만 쓴다
        val adminId = Long.MAX_VALUE

        val ready = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREAD_COUNT)

        val success = AtomicInteger()
        val alreadyDeleted = AtomicInteger()
        val unexpected: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

        val finished = Executors.newFixedThreadPool(THREAD_COUNT).use { executor ->
            for (i in 0 until THREAD_COUNT) {
                executor.submit(
                    Runnable {
                        ready.countDown()
                        try {
                            start.await()
                            userService.deleteUser(adminId, userId)
                            success.incrementAndGet()
                        } catch (e: InvalidException) {
                            // 먼저 커밋된 탈퇴를 잠금 대기 후 확인하고 거절
                            alreadyDeleted.incrementAndGet()
                        } catch (e: Throwable) {
                            unexpected.add(e)
                        } finally {
                            done.countDown()
                        }
                    },
                )
            }

            ready.await()
            start.countDown()
            done.await(10, TimeUnit.SECONDS)
        }

        assertThat(finished).`as`("제한 시간 내 종료").isTrue()
        assertThat(unexpected).`as`("예상하지 못한 예외").isEmpty()
        assertThat(success.get()).`as`("탈퇴 성공은 1건").isEqualTo(1)
        assertThat(alreadyDeleted.get()).`as`("나머지는 이미 탈퇴로 거절").isEqualTo(THREAD_COUNT - 1)

        val result = userRepository.findByIdIncludingDeleted(userId).orElseThrow()
        assertThat(result.isDeleted).`as`("탈퇴 상태").isTrue()
    }

    companion object {
        private const val THREAD_COUNT = 10
    }
}
