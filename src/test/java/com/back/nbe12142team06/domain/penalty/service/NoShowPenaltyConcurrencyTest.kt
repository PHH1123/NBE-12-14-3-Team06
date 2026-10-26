package com.back.nbe12142team06.domain.penalty.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenaltyStatus
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.settlement.service.SettlementService
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 같은 동행인의 정산이 동시에 만들어질 때 패널티가 두 번 차감되지 않는지 확인한다.
 * getNoShowPenalty 는 잠금 없이 PENDING 을 읽기 때문에 두 트랜잭션이 같은 패널티를 집을 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NoShowPenaltyConcurrencyTest {

    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var noShowPenaltyService: NoShowPenaltyService
    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var databaseCleaner: DatabaseCleaner

    private lateinit var client: User
    private lateinit var escort: User

    @BeforeEach
    fun setUp() {
        noShowPenaltyRepository.deleteAll()
        settlementRepository.deleteAll()

        val tag = System.nanoTime().toString()
        val phoneTag = tag.substring(tag.length - 7)

        client = userRepository.save(
            User(
                "client-$tag", "password1!", "client-$tag@test.com", "의뢰인이름",
                Role.CLIENT, Gender.MALE, LocalDate.of(1970, 1, 1), "010${phoneTag}0", "서울"
            )
        )
        escort = userRepository.save(
            User(
                "escort-$tag", "password1!", "escort-$tag@test.com", "동행매니저이름",
                Role.ESCORT, Gender.FEMALE, LocalDate.of(1995, 1, 1), "010${phoneTag}1", "서울"
            )
        )
        escortProfileRepository.save(
            EscortProfile(escort, "자기소개", "오픈은행", "동행매니저이름", "000-1234567-000")
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 동시에 정산이 생성돼도 패널티는 한 번만 차감된다")
    fun penaltyAppliedOnlyOnceUnderConcurrency() {
        // 노쇼 패널티 1건만 쌓인 상태
        noShowPenaltyService.noShow(saveApplication(savePost(), ApplicationStatus.NO_SHOW))

        val firstApplication = saveApplication(savePost(), ApplicationStatus.ACCEPTED)
        val secondApplication = saveApplication(savePost(), ApplicationStatus.ACCEPTED)

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)

        listOf(firstApplication, secondApplication).forEach { application ->
            executor.submit {
                start.await()
                runCatching {
                    settlementService.createSettlement(PAYOUT_AMOUNT, application, escort, LocalDate.now())
                }
            }
        }

        start.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()

        // 패널티 1건은 정산 1건에만 붙는다
        val penalties = noShowPenaltyRepository.findAll()
        assertThat(penalties).hasSize(1)
        assertThat(penalties.first().status).isEqualTo(NoShowPenaltyStatus.APPLIED)

        // 패널티가 차감된 정산도 1건뿐이어야 한다 (둘 다 차감되면 동행인이 20% 를 잃는다)
        val settlements = settlementRepository.findAll()
        assertThat(settlements.count { it.penaltyAmount.signum() > 0 }).isEqualTo(1)
        assertThat(settlements.sumOf { it.penaltyAmount.toLong() }).isEqualTo(PENALTY_AMOUNT.toLong())
    }

    private fun savePost(): Post = postRepository.save(
        Post(
            client = client,
            title = "패널티 경합 공고",
            content = "병원 동행 테스트",
            region = "서울",
            hospitalName = "서울아산병원",
            hospitalAddress = "서울특별시 송파구 올림픽로43길 88",
            hospitalLat = BigDecimal.valueOf(37.52643),
            hospitalLng = BigDecimal.valueOf(127.1096),
            pickupAddress = "서울특별시 중구 세종대로 지하2",
            pickupLat = BigDecimal.valueOf(37.555800),
            pickupLng = BigDecimal.valueOf(126.972000),
            hourlyPay = 15_000,
            recruitStartAt = LocalDateTime.now().minusDays(5),
            recruitEndAt = LocalDateTime.now().minusDays(4),
            escortStartAt = LocalDateTime.now().minusDays(2),
            escortEndAt = LocalDateTime.now().minusDays(2).plusHours(4),
            postStatus = PostStatus.COMPLETED,
        )
    )

    private fun saveApplication(post: Post, status: ApplicationStatus): Application =
        applicationRepository.save(
            Application(post = post, escort = escort, status = status, acceptedPostId = post.id)
        )

    companion object {
        private val PAYOUT_AMOUNT: BigDecimal = BigDecimal.valueOf(60_000)
        // 60,000 → 지급액 54,000 → 패널티 5,400
        private val PENALTY_AMOUNT: BigDecimal = BigDecimal.valueOf(5_400)
    }
}
