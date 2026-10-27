package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.client.SettlementClientRequest
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
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * SettlementService 는 트랜잭션을 걸지 않고 SettlementPersistenceService 가 단계마다 커밋한다.
 * 그 커밋 경계를 검증하는 테스트라서 테스트에 @Transactional 을 걸지 않는다.
 * (테스트가 트랜잭션을 쥐고 있으면 내부 @Transactional 이 거기에 참여해 커밋이 일어나지 않는다)
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementNotTransactionServiceTest {

    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var databaseCleaner: DatabaseCleaner

    @field:MockitoSpyBean private lateinit var settlementPersistenceService: SettlementPersistenceService
    @field:MockitoBean private lateinit var settlementClient: SettlementClient
    @field:MockitoBean private lateinit var noticeService: NoticeService

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    private fun createClient(tag: String): User = User(
        "client-$tag", "password1!", "client-$tag@test.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1970, 1, 1), "010-1111-${tag.takeLast(4)}", "서울"
    )

    private fun createEscort(tag: String): User = User(
        "escort-$tag", "password1!", "escort-$tag@test.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(1995, 1, 1), "010-2222-${tag.takeLast(4)}", "서울"
    )

    private fun createPost(client: User): Post {
        return Post(
            client = client,
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
            recruitStartAt = LocalDateTime.now().minusDays(10),
            recruitEndAt = LocalDateTime.now().minusDays(5),
            escortStartAt = LocalDateTime.now().minusDays(3),
            escortEndAt = LocalDateTime.now().minusDays(3).plusHours(10)
        )
    }

    // 정산 대상(동행 완료 + 승인된 지원 + 정산 전) 한 건을 만든다
    private fun savePendingSettlement(escort: User, client: User): Settlement {
        val post = postRepository.save(createPost(client))
        val application = applicationRepository.save(
            Application(
                post = post,
                escort = escort,
                status = ApplicationStatus.ACCEPTED,
                acceptedPostId = post.id,
            )
        )
        return settlementRepository.save(
            Settlement(
                id = null,
                payoutAmount = BigDecimal.valueOf(135_000),
                platformFee = BigDecimal.valueOf(15_000),
                penaltyAmount = BigDecimal.ZERO,
                settlementStatus = SettlementStatus.PENDING,
                settledDate = LocalDate.now(),
                application = application,
                escort = escort
            )
        )
    }

    private fun saveEscortWithProfile(tag: String): User {
        val escort = userRepository.save(createEscort(tag))
        escortProfileRepository.save(
            EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-$tag")
        )
        return escort
    }

    @Test
    @DisplayName("[SettlementService] 정산 중 상태가 커밋되어 다른 트랜잭션에서도 보인다")
    fun processingSettlementIsCommitted() {
        val escort = saveEscortWithProfile("001")
        val client = userRepository.save(createClient("001"))
        val settlement = savePendingSettlement(escort, client)

        val row = settlementPersistenceService.processingSettlement(settlement.requiredId)

        // 테스트에 트랜잭션이 없으므로 아래 조회는 새 트랜잭션에서 커밋된 상태를 읽는다
        val reloaded = settlementRepository.findByIdOrNull(settlement.requiredId)!!

        assertThat(row).isEqualTo(1)
        assertThat(reloaded.settlementStatus).isEqualTo(SettlementStatus.PROCESSING)
    }

    @Test
    @DisplayName("[SettlementService] 이미 정산 중인 건은 0건 반환 - 중복 정산 차단")
    fun processingSettlementBlocksDuplicate() {
        val escort = saveEscortWithProfile("002")
        val client = userRepository.save(createClient("002"))
        val settlement = savePendingSettlement(escort, client)

        val firstRow = settlementPersistenceService.processingSettlement(settlement.requiredId)
        val secondRow = settlementPersistenceService.processingSettlement(settlement.requiredId)

        assertThat(firstRow).isEqualTo(1)
        assertThat(secondRow).isEqualTo(0)
    }

    @Test
    @DisplayName("[SettlementService] DB 저장이 실패해도 정산 중 상태는 커밋되어 재정산을 막는다")
    fun processingSettlementSurvivesDbFailure() {
        val escort = saveEscortWithProfile("003")
        val client = userRepository.save(createClient("003"))
        val settlement = savePendingSettlement(escort, client)
        val settlementId = settlement.requiredId

        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 135_000))
            .whenever(settlementClient).settlementRequest(any())
        doThrow(PersistenceException("스레드 고갈"))
            .whenever(settlementPersistenceService).updateSettlement(any(), any())

        assertThatThrownBy { settlementService.request(escort.id, settlementId) }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("정산 데이터 저장 중 DB 에러가 발생했습니다.")

        // 송금은 성공했으므로 PROCESSING 이 커밋되어 남아야 한다 (중복 송금 방지)
        assertThat(settlementRepository.findByIdOrNull(settlementId)!!.settlementStatus)
            .isEqualTo(SettlementStatus.PROCESSING)
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러는 일부가 실패해도 성공한 건의 정산을 유지한다")
    fun settlementProcessKeepsSucceededSettlement() {
        val succeedEscort = saveEscortWithProfile("004")
        val failedEscort = saveEscortWithProfile("005")
        val client = userRepository.save(createClient("004"))
        val succeedSettlement = savePendingSettlement(succeedEscort, client)
        val failedSettlement = savePendingSettlement(failedEscort, client)

        doReturn(SettlementClientResponse("123-000000-004", "동행매니저", 135_000))
            .whenever(settlementClient).settlementRequest(any())
        // 실패 대상은 요청 계좌번호로 가른다
        doThrow(InternalServerErrorException(73, "정산 외부 API 호출을 실패했습니다."))
            .whenever(settlementClient).settlementRequest(
                argThat<SettlementClientRequest> { req_list.first().account_num == "123-000000-005" }
            )

        val counts = settlementService.settlementProcess()

        assertThat(counts[0]).isEqualTo(2)
        assertThat(counts[1]).isEqualTo(1)
        assertThat(counts[2]).isEqualTo(1)

        // 성공 건은 COMPLETED 로 커밋되고, 실패 건은 다음 스케줄링에서 재시도되도록 FAILED 로 남는다
        assertThat(settlementRepository.findByIdOrNull(succeedSettlement.requiredId)!!.settlementStatus)
            .isEqualTo(SettlementStatus.COMPLETED)
        assertThat(settlementRepository.findByIdOrNull(failedSettlement.requiredId)!!.settlementStatus)
            .isEqualTo(SettlementStatus.FAILED)
    }
}
