package com.back.nbe12142team06.concurrency

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [동시성 시나리오 A] 같은 동행인이 같은 공고에 동시 지원.
 *
 * 이 클래스의 테스트 메서드에는 @Transactional 을 붙이지 않는다.
 * 각 스레드가 실제로 자기 트랜잭션을 커밋해야 경쟁 상태가 재현된다.
 * (테스트에 @Transactional 을 붙이면 모든 스레드가 같은 트랜잭션/커넥션을 공유하게 되어
 * 경쟁 자체가 발생하지 않는다.)
 *
 * dev 의 ApplicationConcurrencyTest#concurrentApply 와 시나리오(같은 동행인의 동시 지원)는
 * 동일하지만 검증 대상이 다르다. dev 쪽은 apply() 안에서 Post 를 비관적 쓰기 락
 * (findByIdWithLock) 으로 조회해 지원 요청 자체를 직렬화하는 것을 검증하는 반면,
 * 이 테스트는 Application 의 (active_post_id, escort_id) unique 제약이 최종 방어선으로
 * 동작해 중복 저장을 막는다는 것을 검증한다. 두 테스트는 서로 다른 안전장치를 검증하므로
 * 둘 다 유지한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplyConcurrencyTest {

    @Autowired
    private lateinit var applicationService: ApplicationService

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    private var postId: Long = 0
    private var escortId: Long = 0

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()
        val tag = System.nanoTime().toString()
        val phoneTag = tag.substring(tag.length - 7)

        val client = userRepository.save(
            User(
                username = "apply-client-$tag",
                password = "pw",
                email = "apply-client-$tag@test.com",
                name = "의뢰인",
                role = Role.CLIENT,
                gender = Gender.MALE,
                birthDate = LocalDate.of(1970, 1, 1),
                phoneNum = "010${phoneTag}1",
                region = "수원",
            ),
        )

        val escort = userRepository.save(
            User(
                username = "apply-escort-$tag",
                password = "pw",
                email = "apply-escort-$tag@test.com",
                name = "동행인",
                role = Role.ESCORT,
                gender = Gender.FEMALE,
                birthDate = LocalDate.of(1995, 1, 1),
                phoneNum = "010${phoneTag}2",
                region = "수원",
            ),
        )

        val escortProfile = EscortProfile(escort, "자기소개", "국민은행", "동행인", "1234567890")
        escortProfileRepository.save(escortProfile)

        transactionTemplate.executeWithoutResult {
            escortProfileRepository.verify(escort.id, LocalDateTime.now())
        }

        val post = postRepository.save(
            Post(
                client = client,
                title = "테스트 공고",
                content = "병원 동행 테스트",
                region = "수원",
                hospitalName = "아주대학교병원",
                hospitalAddress = "경기도 수원시",
                hospitalLat = BigDecimal.valueOf(37.2795),
                hospitalLng = BigDecimal.valueOf(127.0476),
                pickupAddress = "경기도 수원시 팔달구",
                pickupLat = BigDecimal.valueOf(37.2636),
                pickupLng = BigDecimal.valueOf(127.0286),
                hourlyPay = 15000,
                recruitStartAt = LocalDateTime.now().minusMinutes(1),
                recruitEndAt = LocalDateTime.now().plusDays(1),
                escortStartAt = LocalDateTime.now().plusDays(2),
                escortEndAt = LocalDateTime.now().plusDays(2).plusHours(3),
            ),
        )

        postId = post.requiredId
        escortId = escort.id
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    @Test
    @DisplayName("[동시성 A] 같은 동행인이 같은 공고에 10개 스레드로 동시 지원하면 지원은 1건만 생겨야 한다")
    fun 같은_동행인이_같은_공고에_동시_지원() {

        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger()
        val failureCount = AtomicInteger()

        repeat(threadCount) {
            executorService.submit(
                Runnable {
                    try {
                        readyLatch.countDown()
                        startLatch.await()
                        applicationService.apply(postId, escortId)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                },
            )
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executorService.shutdown()

        val applications = applicationRepository.findAll()
            .filter { it.post.id == postId && it.escort.id == escortId }

        // ApplicationService.apply() 의 existsByPostAndEscortAndStatusNot() 검사는
        // check-then-act 라 여러 스레드가 동시에 통과할 수 있지만, Application 의
        // (post_id, escort_id, status) unique 제약이 최종 방어선이 되어 실제로
        // 저장되는 지원은 1건뿐이다.
        assertThat(applications).hasSize(1)
        assertThat(successCount.get()).isEqualTo(1)
    }
}
