package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.client.SettlementClientResponse
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class SettlementConcurrencyTest {

    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var databaseCleaner: DatabaseCleaner
    @field:Autowired private lateinit var transactionTemplate: TransactionTemplate

    @field:MockitoBean private lateinit var settlementClient: SettlementClient

    private lateinit var client: User
    private lateinit var escort: User

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

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

        val savedProfile = escortProfileRepository.save(
            EscortProfile(escort, "자기소개", "오픈은행", "동행매니저이름", "000-1234567-000")
        )
        transactionTemplate.executeWithoutResult {
            escortProfileRepository.verify(savedProfile.userId!!, LocalDateTime.now())
        }
    }

    @Test
    @DisplayName("[SettlementService] 동시성 스케줄러와 정산 요청")
    fun concurrency1() {
        val post = savePost("정산 경합 공고", PostStatus.COMPLETED)
        val application = saveApplication(post, ApplicationStatus.ACCEPTED, post.id)
        val settlement = saveSettlement(application, BigDecimal.ZERO)

        doAnswer {
            Thread.sleep(500)
            SettlementClientResponse("000-1234567-000", "동행매니저이름", SETTLEMENT_AMOUNT.toInt())
        }.whenever(settlementClient).settlementRequest(any())

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)

        executor.submit {
            start.await()
            runCatching { settlementService.request(escort.id, settlement.requiredId) }
        }
        executor.submit {
            start.await()
            runCatching { settlementService.settlementProcess() }
        }

        start.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()

        // 정산 중 선점(update ... where status in (PENDING, FAILED))에서 한쪽만 통과한다
        verify(settlementClient, times(1)).settlementRequest(any())
        assertThat(settlementRepository.findById(settlement.requiredId).get().settlementStatus)
            .isEqualTo(SettlementStatus.COMPLETED)
    }

    private fun savePost(title: String, status: PostStatus): Post {
        return postRepository.save(
            Post(
                client = client,
                title = title,
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
                postStatus = status,
            )
        )
    }

    private fun saveApplication(post: Post, status: ApplicationStatus, acceptedPostId: Long?): Application {
        return applicationRepository.save(
            Application(post = post, escort = escort, status = status, acceptedPostId = acceptedPostId)
        )
    }

    private fun saveSettlement(application: Application, penaltyAmount: BigDecimal): Settlement {
        return settlementRepository.save(
            Settlement(
                id = null,
                payoutAmount = SETTLEMENT_AMOUNT.subtract(penaltyAmount),
                platformFee = PAYOUT_AMOUNT.subtract(SETTLEMENT_AMOUNT),
                penaltyAmount = penaltyAmount,
                settlementStatus = SettlementStatus.PENDING,
                settledDate = LocalDate.now(),
                application = application,
                escort = escort
            )
        )
    }

    companion object {
        private val PAYOUT_AMOUNT: BigDecimal = BigDecimal.valueOf(60_000)
        private val SETTLEMENT_AMOUNT: BigDecimal = PAYOUT_AMOUNT.multiply(BigDecimal.valueOf(0.9))
    }
}
