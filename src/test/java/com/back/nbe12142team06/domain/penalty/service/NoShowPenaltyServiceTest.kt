package com.back.nbe12142team06.domain.penalty.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenaltyStatus
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoShowPenaltyServiceTest {

    // 시급
    private val hourlyPay = 15_000
    // 동행 시간
    private val hours: BigDecimal = BigDecimal.TEN
    // 의뢰인 결제 금액
    private val payoutAmount: BigDecimal = hours.multiply(BigDecimal.valueOf(hourlyPay.toLong()))
    // 플랫폼 수수료
    private val platformFee: BigDecimal = payoutAmount.multiply(BigDecimal.valueOf(0.1))
    // 패널티 차감 전 정산 금액
    private val baseAmount: BigDecimal = payoutAmount.subtract(platformFee)
    // 패널티 금액
    private val penaltyAmount: BigDecimal = baseAmount.multiply(BigDecimal.valueOf(0.1))

    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var noShowPenaltyService: NoShowPenaltyService
    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var settlementRepository: SettlementRepository

    private fun createPost(client: User, status: PostStatus): Post {
        return Post.builder()
            .title("정형외과 동행 구합니다")
            .content("무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.")
            .region("서울")
            .hospitalName("서울성모병원")
            .hospitalAddress("서울 서초구 반포대로 222")
            .hospitalLat(BigDecimal.valueOf(37.5012743))
            .hospitalLng(BigDecimal.valueOf(127.0051893))
            .pickupAddress("서울 서초구 잠원동 10-1")
            .pickupLat(BigDecimal.valueOf(37.5160000))
            .pickupLng(BigDecimal.valueOf(127.0200000))
            .hourlyPay(hourlyPay)
            .recruitStartAt(LocalDateTime.now().plusDays(1))
            .recruitEndAt(LocalDateTime.now().plusDays(6))
            .escortStartAt(LocalDateTime.now().plusDays(7))
            .escortEndAt(LocalDateTime.now().plusDays(7).plusHours(hours.toLong()))
            .postStatus(status)
            .client(client)
            .build()
    }

    private fun createClient(): User = User(
        "client01", "password1!", "client01@email.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
    )

    private fun createEscort(): User = User(
        "escort01", "password1!", "escort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
    )

    private fun createApplication(escort: User, post: Post, status: ApplicationStatus): Application {
        return Application.builder()
            .post(post)
            .escort(escort)
            .status(status)
            .acceptedPostId(post.id)
            .activePostId(post.id)
            .build()
    }

    private fun createSettlement(application: Application, escort: User): Settlement = Settlement(
        id = null,
        payoutAmount = baseAmount.subtract(penaltyAmount),
        platformFee = platformFee,
        penaltyAmount = penaltyAmount,
        settlementStatus = SettlementStatus.PENDING,
        settledDate = LocalDate.now().plusDays(1),
        application = application,
        escort = escort
    )

    // 노쇼가 발생한 지원 하나를 DB 에 올려둔다. (의뢰인 - 공고 - 동행 매니저 - 지원)
    private fun setUpNoShowApplication(): Application {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())
        val post = postRepository.save(createPost(client, PostStatus.COMPLETED))

        return applicationRepository.save(createApplication(escort, post, ApplicationStatus.NO_SHOW))
    }

    @Test
    @DisplayName("[NoShowPenaltyService] APPLIED 패널티는 재적용 대상에서 제외된다")
    fun appliedPenaltyNotReapplied() {
        val application = setUpNoShowApplication()
        val escortId = application.escort!!.id

        noShowPenaltyService.noShow(application)
        em.flush()

        // 생성 직후에는 PENDING 이라 적용 대상으로 조회된다
        val pendingPenalty: NoShowPenalty = noShowPenaltyService.getNoShowPenalty(escortId)!!
        assertThat(pendingPenalty.status).isEqualTo(NoShowPenaltyStatus.PENDING)

        val settlement = settlementRepository.save(createSettlement(application, application.escort!!))
        noShowPenaltyService.applyPenalty(escortId, penaltyAmount, baseAmount, settlement, pendingPenalty)

        em.flush()
        em.clear()

        // 적용 결과가 패널티에 남는다
        val appliedPenalty = noShowPenaltyRepository.findById(pendingPenalty.id!!).orElseThrow()
        assertThat(appliedPenalty.status).isEqualTo(NoShowPenaltyStatus.APPLIED)
        assertThat(appliedPenalty.amount).isEqualByComparingTo(penaltyAmount)
        assertThat(appliedPenalty.baseAmount).isEqualByComparingTo(baseAmount)
        assertThat(appliedPenalty.settlement!!.id).isEqualTo(settlement.id)

        // 이후 정산에서는 다시 적용 대상으로 잡히지 않는다 (findByEscortIdAndStatus 가 PENDING 만 조회)
        assertThat(noShowPenaltyService.getNoShowPenalty(escortId)).isNull()
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 같은 지원에 패널티 2번 생성 방지")
    fun penaltyUniqueConstraint() {
        val application = setUpNoShowApplication()

        noShowPenaltyService.noShow(application)
        em.flush()

        // application_id 에 unique 가 걸려 있어 같은 지원으로는 두 번째 패널티를 만들 수 없다
        assertThatThrownBy {
            noShowPenaltyService.noShow(application)
            em.flush()
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        em.clear()
        assertThat(noShowPenaltyRepository.findAll()).hasSize(1)
    }
}
