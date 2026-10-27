package com.back.nbe12142team06.concurrency

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.review.dto.ReviewWriteRequest
import com.back.nbe12142team06.domain.review.service.ReviewService
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [동시성 시나리오 C] 한 동행인의 서로 다른 완료된 동행 건에 리뷰 2건이 동시에 작성됨.
 *
 * 이 클래스의 테스트 메서드에는 @Transactional 을 붙이지 않는다.
 * 각 스레드가 실제로 자기 트랜잭션을 커밋해야 경쟁 상태가 재현된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewRatingConcurrencyTest {

    @Autowired
    private lateinit var reviewService: ReviewService

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    private var escortId: Long = 0
    private var clientId1: Long = 0
    private var clientId2: Long = 0
    private var applicationId1: Long = 0
    private var applicationId2: Long = 0

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()
        val tag = System.nanoTime().toString()
        val phoneTag = tag.substring(tag.length - 7)

        val escort = userRepository.save(
            User(
                username = "report-escort-$tag",
                password = "pw",
                email = "report-escort-$tag@test.com",
                name = "동행인",
                role = Role.ESCORT,
                gender = Gender.FEMALE,
                birthDate = LocalDate.of(1995, 1, 1),
                phoneNum = "010${phoneTag}1",
                region = "수원",
            ),
        )

        val client1 = userRepository.save(
            User(
                username = "review-client1-$tag",
                password = "pw",
                email = "review-client1-$tag@test.com",
                name = "의뢰인1",
                role = Role.CLIENT,
                gender = Gender.MALE,
                birthDate = LocalDate.of(1950, 1, 1),
                phoneNum = "010${phoneTag}2",
                region = "수원",
            ),
        )

        val client2 = userRepository.save(
            User(
                username = "review-client2-$tag",
                password = "pw",
                email = "review-client2-$tag@test.com",
                name = "의뢰인2",
                role = Role.CLIENT,
                gender = Gender.MALE,
                birthDate = LocalDate.of(1955, 1, 1),
                phoneNum = "010${phoneTag}3",
                region = "수원",
            ),
        )

        escortProfileRepository.save(EscortProfile(escort, "자기소개", "국민은행", "동행인", "1234567890"))

        val post1 = postRepository.save(completedPost(client1))
        val post2 = postRepository.save(completedPost(client2))

        val application1 = applicationRepository.save(
            Application(post = post1, escort = escort, status = ApplicationStatus.ACCEPTED),
        )
        val application2 = applicationRepository.save(
            Application(post = post2, escort = escort, status = ApplicationStatus.ACCEPTED),
        )

        escortId = escort.id
        clientId1 = client1.id
        clientId2 = client2.id
        applicationId1 = application1.requiredId
        applicationId2 = application2.requiredId
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    // 기존 테스트와 동일하게 완료 상태(COMPLETED) 공고를 생성한다.
    private fun completedPost(client: User): Post {
        return Post(
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
            recruitStartAt = LocalDateTime.now().minusDays(3),
            recruitEndAt = LocalDateTime.now().minusDays(2),
            escortStartAt = LocalDateTime.now().minusDays(1),
            escortEndAt = LocalDateTime.now().minusDays(1).plusHours(3),
            postStatus = PostStatus.COMPLETED,
        )
    }

    @Test
    @DisplayName("[동시성 C] 같은 동행인에게 리뷰 2건이 동시에 작성되면 평점 합계와 개수가 모두 반영되어야 한다")
    fun 같은_동행인에게_리뷰_2건이_동시에_작성() {

        val rating1 = 4
        val rating2 = 5

        val executorService = Executors.newFixedThreadPool(2)
        val readyLatch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)

        val successCount = AtomicInteger()
        val failures = CopyOnWriteArrayList<Throwable>()

        executorService.submit(
            Runnable {
                try {
                    readyLatch.countDown()
                    startLatch.await()
                    reviewService.write(applicationId1, clientId1, ReviewWriteRequest(rating1, null, null))
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failures.add(e)
                } finally {
                    doneLatch.countDown()
                }
            },
        )

        executorService.submit(
            Runnable {
                try {
                    readyLatch.countDown()
                    startLatch.await()
                    reviewService.write(applicationId2, clientId2, ReviewWriteRequest(rating2, null, null))
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failures.add(e)
                } finally {
                    doneLatch.countDown()
                }
            },
        )

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        executorService.shutdown()

        assertThat(failures).isEmpty()
        assertThat(successCount.get()).isEqualTo(2)

        val escortProfile = escortProfileRepository.findById(escortId).orElseThrow()

        // EscortProfileRepository.addRating() 은 엔티티를 읽어 절대값으로 덮어쓰는 대신
        // DB 에서 "현재 값 + 증가분" 을 원자적으로 계산하는 벌크 UPDATE 라, 두 트랜잭션이
        // 동시에 커밋해도 lost update 없이 두 별점이 모두 반영된다.
        assertThat(escortProfile.ratingCount).isEqualTo(2)
        assertThat(escortProfile.ratingSum).isEqualTo(rating1 + rating2)
    }
}
