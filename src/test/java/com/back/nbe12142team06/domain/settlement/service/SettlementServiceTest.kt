package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
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
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementServiceTest {

    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository

    @field:MockitoSpyBean private lateinit var settlementPersistenceService: SettlementPersistenceService
    @field:MockitoBean private lateinit var settlementClient: SettlementClient
    @field:MockitoBean private lateinit var noticeService: NoticeService

    private fun createPost(client: User, status: PostStatus, hour: Long = 10): Post {
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
            postStatus = status,
            client = client,
        )
    }

    private fun createClient(): User = User(
        "client01", "password1!", "client01@email.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
    )

    private fun createEscort(): User = User(
        "escort01", "password1!", "escort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
    )

    private fun createSettlement(application: Application, escort: User, status: SettlementStatus): Settlement {
        val amount = BigDecimal.TEN.multiply(BigDecimal.valueOf(15_000))
        val payoutAmount = amount.multiply(BigDecimal.valueOf(0.9))
        val platformFee = amount.multiply(BigDecimal.valueOf(0.1))

        return Settlement(
            id = null,
            payoutAmount = payoutAmount,
            platformFee = platformFee,
            penaltyAmount = BigDecimal.ZERO,
            settlementStatus = status,
            settledDate = LocalDate.now().plusDays(1),
            application = application,
            escort = escort
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

    private fun createEscortProfile(escort: User): EscortProfile {
        return EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-123")
    }

    private fun saveApplication(escort: User, client: User): Application {
        val post = postRepository.save(createPost(client, PostStatus.COMPLETED))
        return applicationRepository.save(createApplication(escort, post))
    }

    @Test
    @DisplayName("[SettlementService] 외부 송금 실패 시 FAILED")
    fun settlementThirdPartyApiFailed() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        escortProfileRepository.save(createEscortProfile(escort))
        val settlement = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.PENDING)
        )

        doThrow(InternalServerErrorException("정산 외부 API 호출 중 에러 발생"))
            .whenever(settlementClient).settlementRequest(any())

        assertThatThrownBy { settlementService.request(escort.id, settlement.requiredId) }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("정산에 실패했습니다.")

        em.flush()
        em.clear()

        assertThat(settlementRepository.findById(settlement.requiredId).get().settlementStatus)
            .isEqualTo(SettlementStatus.FAILED)
    }

    @Test
    @DisplayName("[SettlementService] FAILED → 재시도로 PROCESSING 가능")
    fun settlementRequestStatusFailed() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        escortProfileRepository.save(createEscortProfile(escort))
        val settlement = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.FAILED)
        )

        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 150_000))
            .whenever(settlementClient).settlementRequest(any())

        settlementService.request(escort.id, settlement.requiredId)

        em.flush()
        em.clear()

        assertThat(settlementRepository.findById(settlement.requiredId).get().settlementStatus)
            .isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[SettlementService] PROCESSING/COMPLETED 재요청 거부")
    fun settlementRequestStatusProcessingAndCompleted() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        escortProfileRepository.save(createEscortProfile(escort))

        val settlementProcessing = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.PROCESSING)
        )
        val settlementCompleted = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.COMPLETED)
        )

        // 정산 중은 조회는 되지만 상태 선점에서 막히고, 정산 완료는 조회 자체가 되지 않는다
        assertThatThrownBy { settlementService.request(escort.id, settlementProcessing.requiredId) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 정산 중이거나 정산이 완료되었습니다.")
        assertThatThrownBy { settlementService.request(escort.id, settlementCompleted.requiredId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("정산 데이터를 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("[SettlementService] 정산 예정일 전 요청 가능")
    fun settlementEarlyRequest() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        escortProfileRepository.save(createEscortProfile(escort))
        val settlement = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.PENDING)
        )

        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 150_000))
            .whenever(settlementClient).settlementRequest(any())

        settlementService.request(escort.id, settlement.requiredId)

        em.flush()
        em.clear()

        val completedSettlement = settlementRepository.findById(settlement.requiredId).get()
        assertThat(completedSettlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
        // 정산 예정일(내일)은 그대로 유지된다
        assertThat(completedSettlement.settledDate).isEqualTo(LocalDate.now().plusDays(1))
    }

    @Test
    @DisplayName("[SettlementService] 같은 지원에 정산 2번 생성 방지(unique)")
    fun settlementUniqueConstraint() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        val application = saveApplication(escort, client)

        settlementRepository.save(createSettlement(application, escort, SettlementStatus.PENDING))

        assertThatThrownBy {
            settlementRepository.save(createSettlement(application, escort, SettlementStatus.PENDING))
        }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    @DisplayName("[SettlementService] DB 저장 실패, 실제로는 성공한 경우 PROCESSING으로 상태가 변경되며 재정산하지 못한다.")
    fun settlementDbFailedReRequest() {
        val escort = userRepository.save(createEscort())
        val client = userRepository.save(createClient())
        escortProfileRepository.save(createEscortProfile(escort))
        val settlement = settlementRepository.save(
            createSettlement(saveApplication(escort, client), escort, SettlementStatus.PENDING)
        )

        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 150_000))
            .whenever(settlementClient).settlementRequest(any())
        doThrow(PersistenceException("스레드 고갈"))
            .whenever(settlementPersistenceService).updateSettlement(any(), any())

        assertThatThrownBy { settlementService.request(escort.id, settlement.requiredId) }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessageContaining("DB 에러")

        assertThatThrownBy { settlementService.request(escort.id, settlement.requiredId) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 정산 중이거나 정산이 완료되었습니다.")
    }
}
