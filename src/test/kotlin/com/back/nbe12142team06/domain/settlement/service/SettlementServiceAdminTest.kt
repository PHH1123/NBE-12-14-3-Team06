package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.dto.SettlementAdminResponse
import com.back.nbe12142team06.domain.settlement.dto.SettlementAnalyticsResponse
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementServiceAdminTest {

    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository

    @field:MockitoBean private lateinit var settlementClient: SettlementClient

    private fun createSettlement(
        payoutAmount: BigDecimal,
        platformFee: BigDecimal,
        penaltyAmount: BigDecimal,
        status: SettlementStatus,
        application: Application,
        escort: User
    ): Settlement {
        return Settlement(
            id = null,
            payoutAmount = payoutAmount,
            platformFee = platformFee,
            penaltyAmount = penaltyAmount,
            settlementStatus = status,
            settledDate = LocalDate.now(),
            application = application,
            escort = escort
        )
    }

    // 수수료는 지급액의 1/9 (= 총액의 10%)
    private fun createSettlement(
        payoutAmount: BigDecimal,
        status: SettlementStatus,
        application: Application,
        escort: User
    ): Settlement = createSettlement(
        payoutAmount, payoutAmount.divide(BigDecimal.valueOf(9.0)), BigDecimal.ZERO,
        status, application, escort
    )

    private fun createPost(client: User, hour: Long = 4): Post {
        return Post(
            title = "정형외과 동행 구합니다",
            content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
            region = "서울",
            hospitalName = "서울성모병원",
            hospitalAddress = "서울 서초구 반포대로 222",
            hospitalLat = BigDecimal.valueOf(37.5012743),
            hospitalLng = BigDecimal.valueOf(127.0051893),
            pickupAddress = "서울 서초구 잠원동 10-1",
            pickupLat = BigDecimal.valueOf(37.5160000),
            pickupLng = BigDecimal.valueOf(127.0200000),
            hourlyPay = 15_000,
            recruitStartAt = LocalDateTime.now().plusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(6),
            escortStartAt = LocalDateTime.now().plusDays(7),
            escortEndAt = LocalDateTime.now().plusDays(7).plusHours(hour),
            postStatus = PostStatus.COMPLETED,
            client = client,
        )
    }

    private fun createApplication(escort: User, post: Post): Application {
        return Application(
            post = post,
            escort = escort,
            status = ApplicationStatus.ACCEPTED,
            acceptedPostId = post.id,
            activePostId = post.id,
        )
    }

    private fun createClient(): User = User(
        "aclient01", "password1!", "aclient01@email.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1239", "서울"
    )

    private fun createEscort(): User = User(
        "aescort01", "password1!", "aescort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5679", "서울"
    )

    private fun createEscortProfile(escort: User): EscortProfile {
        return EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-123")
    }

    private fun saveApplication(client: User, escort: User): Application {
        val post = postRepository.save(createPost(client))
        return applicationRepository.save(createApplication(escort, post))
    }

    @Test
    @DisplayName("[SettlementService] 관리자 기간별 정산 금액 및 건수 조회")
    fun getSettlementByPeriod() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())

        // 정산 완료 - 지급 135_000 / 수수료 15_000 / 기부 0
        val settlement1 = createSettlement(
            BigDecimal.valueOf(135_000), BigDecimal.valueOf(15_000), BigDecimal.ZERO,
            SettlementStatus.COMPLETED, saveApplication(client, escort), escort
        )
        // 정산 완료 - 지급 121_500 / 수수료 15_000 / 기부 13_500
        val settlement2 = createSettlement(
            BigDecimal.valueOf(121_500), BigDecimal.valueOf(15_000), BigDecimal.valueOf(13_500),
            SettlementStatus.COMPLETED, saveApplication(client, escort), escort
        )
        // 정산 전 - 미정산 135_000
        val settlement3 = createSettlement(
            BigDecimal.valueOf(135_000), BigDecimal.valueOf(15_000), BigDecimal.ZERO,
            SettlementStatus.PENDING, saveApplication(client, escort), escort
        )
        // 정산 실패 - 미정산 90_000
        val settlement4 = createSettlement(
            BigDecimal.valueOf(90_000), BigDecimal.valueOf(10_000), BigDecimal.ZERO,
            SettlementStatus.FAILED, saveApplication(client, escort), escort
        )
        // 정산 중 - 오류 45_000
        val settlement5 = createSettlement(
            BigDecimal.valueOf(45_000), BigDecimal.valueOf(5_000), BigDecimal.ZERO,
            SettlementStatus.PROCESSING, saveApplication(client, escort), escort
        )

        settlementRepository.saveAll(listOf(settlement1, settlement2, settlement3, settlement4, settlement5))

        // 관리자 집계 쿼리는 settledDate 가 아니라 createdAt 기준으로 기간 내 '모든' 정산을 읽는다
        // (SettlementRepository.findAnalytics). 다른 테스트가 커밋해 둔 정산도 createdAt 이 실행 시각이라
        // 항상 이 기간에 들어오므로, 이 테스트가 만든 건만 남겨 집계한다.
        val myIds = listOf(settlement1, settlement2, settlement3, settlement4, settlement5)
            .map { it.requiredId }
            .toSet()

        val settlements = settlementService
            .getAnalytics(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1))
            .filter { it.requiredId in myIds }

        // 서비스 쿼리가 이 테스트의 정산 5건을 실제로 읽어왔는지 먼저 확인한다
        // (빈 목록을 집계해도 0 이 나오므로, 이 단정이 없으면 헛통과할 수 있다)
        assertThat(settlements).hasSize(5)

        val result = SettlementAnalyticsResponse.from(settlements)

        assertThat(result.totalSettlementAmount).isEqualTo(526_500L)
        assertThat(result.settlementCount).isEqualTo(5L)
        assertThat(result.totalSettledAmount).isEqualTo(256_500L)
        assertThat(result.settledCount).isEqualTo(2L)
        assertThat(result.totalUnSettledAmount).isEqualTo(225_000L)
        assertThat(result.unSettledCount).isEqualTo(2L)
        assertThat(result.totalPlatformFee).isEqualTo(30_000L)
        assertThat(result.platformFeeCount).isEqualTo(2L)
        assertThat(result.totalDonationAmount).isEqualTo(13_500L)
        assertThat(result.donationCount).isEqualTo(1L)
        assertThat(result.totalErrorSettlementAmount).isEqualTo(45_000L)
        assertThat(result.errorSettlementCount).isEqualTo(1L)
    }

    @Test
    @DisplayName("[SettlementService] 관리자 기간별 정산 목록 조회")
    fun settlementList() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())

        val settlement1 = createSettlement(
            BigDecimal.valueOf(135_000), SettlementStatus.COMPLETED, saveApplication(client, escort), escort
        )
        val settlement2 = createSettlement(
            BigDecimal.valueOf(121_500), SettlementStatus.PENDING, saveApplication(client, escort), escort
        )
        val settlement3 = createSettlement(
            BigDecimal.valueOf(90_000), SettlementStatus.FAILED, saveApplication(client, escort), escort
        )
        val settlement4 = createSettlement(
            BigDecimal.valueOf(45_000), SettlementStatus.PROCESSING, saveApplication(client, escort), escort
        )

        settlementRepository.saveAll(listOf(settlement1, settlement2, settlement3, settlement4))

        val pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id")

        val settlementPage = settlementService.getSettlementForAdmin(
            LocalDate.now().minusDays(7), LocalDate.now().plusDays(1), pageable
        )

        // 위 테스트와 같은 이유로 이 테스트가 만든 정산만 남긴다.
        // id 내림차순이라 가장 나중에 만든 이 테스트의 정산들이 첫 페이지 앞쪽에 온다.
        val myIds = listOf(settlement1, settlement2, settlement3, settlement4)
            .map { it.requiredId }
            .toSet()

        val result = settlementPage.content
            .filter { it.requiredId in myIds }
            .map { SettlementAdminResponse(it) }

        assertThat(result).hasSize(4)
        assertThat(result.first().id).isEqualTo(settlement4.requiredId)
        assertThat(result.first().payoutAmount).isEqualTo(45_000)
        assertThat(result.first().status).isEqualTo(SettlementStatus.PROCESSING)
        assertThat(result.first().name).isEqualTo("동행매니저")
        assertThat(result.last().id).isEqualTo(settlement1.requiredId)
        assertThat(result.last().payoutAmount).isEqualTo(135_000)
        assertThat(result.last().status).isEqualTo(SettlementStatus.COMPLETED)
        assertThat(result.last().userId).isEqualTo(escort.id)
    }

    // deprecated - PROCESSING 상태도 정산 되도록 하면 관리자의 부주의 이중 정산의 위험성이 있어 삭제 예정
//    @Test
//    @DisplayName("[SettlementService] 관리자 정산 요청 - PROCESSING 상태여도 정산된다")
//    fun requestForAdmin() {
//        val escort = userRepository.save(createEscort())
//        val client = userRepository.save(createClient())
//        escortProfileRepository.save(createEscortProfile(escort))
//
//        val settlement = settlementRepository.save(
//            createSettlement(
//                BigDecimal.valueOf(135_000), BigDecimal.valueOf(15_000), BigDecimal.ZERO,
//                SettlementStatus.PROCESSING, saveApplication(client, escort), escort
//            )
//        )
//
//        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 135_000))
//            .whenever(settlementClient).settlementRequest(any())
//
//        settlementService.requestForAdmin(settlement.requiredId)
//
//        em.flush()
//        em.clear()
//
//        assertThat(settlementRepository.findById(settlement.requiredId).get().settlementStatus)
//            .isEqualTo(SettlementStatus.COMPLETED)
//    }
}
