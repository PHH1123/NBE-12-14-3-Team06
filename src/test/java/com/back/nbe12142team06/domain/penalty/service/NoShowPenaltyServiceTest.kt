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
import com.back.nbe12142team06.domain.settlement.service.SettlementService
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
    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var settlementRepository: SettlementRepository

    private fun createPost(client: User, status: PostStatus): Post {
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
            hourlyPay = hourlyPay,
            recruitStartAt = LocalDateTime.now().plusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(6),
            escortStartAt = LocalDateTime.now().plusDays(7),
            escortEndAt = LocalDateTime.now().plusDays(7).plusHours(hours.toLong()),
            postStatus = status,
            client = client,
        )
    }

    private fun createClient(tag: String = "01"): User = User(
        "client$tag", "password1!", "client$tag@email.com", "의뢰인",
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-12$tag", "서울"
    )

    private fun createEscort(tag: String = "01"): User = User(
        "escort$tag", "password1!", "escort$tag@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-56$tag", "서울"
    )

    private fun createApplication(escort: User, post: Post, status: ApplicationStatus): Application {
        return Application(
            post = post,
            escort = escort,
            status = status,
            acceptedPostId = post.id,
            activePostId = post.id,
        )
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

    // 지원 하나를 DB 에 올려둔다. (공고 - 지원)
    private fun saveApplication(
        client: User,
        escort: User,
        status: ApplicationStatus = ApplicationStatus.NO_SHOW
    ): Application {
        val post = postRepository.save(createPost(client, PostStatus.COMPLETED))
        return applicationRepository.save(createApplication(escort, post, status))
    }

    // 노쇼가 발생한 지원 하나를 DB 에 올려둔다. (의뢰인 - 공고 - 동행 매니저 - 지원)
    private fun setUpNoShowApplication(): Application {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())

        return saveApplication(client, escort)
    }

    @Test
    @DisplayName("[NoShowPenaltyService] APPLIED 패널티는 재적용 대상에서 제외된다")
    fun appliedPenaltyNotReapplied() {
        val application = setUpNoShowApplication()
        val escortId = application.escort.id

        noShowPenaltyService.noShow(application)
        em.flush()

        // 생성 직후에는 PENDING 이라 적용 대상으로 조회된다
        val pendingPenalty: NoShowPenalty = noShowPenaltyService.getNoShowPenalty(escortId)!!
        assertThat(pendingPenalty.status).isEqualTo(NoShowPenaltyStatus.PENDING)

        val settlement = settlementRepository.save(createSettlement(application, application.escort))
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

    @Test
    @DisplayName("[NoShowPenaltyService] 패널티가 2건 쌓여 있으면 정산 1건에서 오래된 1건만 소비한다")
    fun onlyOldestPenaltyAppliedPerSettlement() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())

        // 서로 다른 지원에서 노쇼가 두 번 발생
        noShowPenaltyService.noShow(saveApplication(client, escort))
        noShowPenaltyService.noShow(saveApplication(client, escort))
        em.flush()

        val completed = saveApplication(client, escort, ApplicationStatus.ACCEPTED)
        val settlement = settlementService.createSettlement(
            payoutAmount, completed, escort, LocalDate.now().plusDays(1)
        )
        em.flush()
        em.clear()

        // 정산 1건에는 패널티 1건만 차감된다
        assertThat(settlement.penaltyAmount).isEqualByComparingTo(penaltyAmount)

        val penalties = noShowPenaltyRepository.findAll().sortedBy { it.id }
        assertThat(penalties).hasSize(2)
        assertThat(penalties[0].status).isEqualTo(NoShowPenaltyStatus.APPLIED)
        assertThat(penalties[0].settlement!!.id).isEqualTo(settlement.id)
        // 남은 패널티는 다음 정산을 기다린다
        assertThat(penalties[1].status).isEqualTo(NoShowPenaltyStatus.PENDING)
        assertThat(penalties[1].amount).isNull()
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 패널티 2건은 정산 2건에서 각각 소비된다")
    fun eachSettlementConsumesOnePenalty() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())

        noShowPenaltyService.noShow(saveApplication(client, escort))
        noShowPenaltyService.noShow(saveApplication(client, escort))
        em.flush()

        val firstSettlement = settlementService.createSettlement(
            payoutAmount, saveApplication(client, escort, ApplicationStatus.ACCEPTED), escort, LocalDate.now()
        )
        val secondSettlement = settlementService.createSettlement(
            payoutAmount, saveApplication(client, escort, ApplicationStatus.ACCEPTED), escort, LocalDate.now()
        )
        em.flush()
        em.clear()

        assertThat(firstSettlement.penaltyAmount).isEqualByComparingTo(penaltyAmount)
        assertThat(secondSettlement.penaltyAmount).isEqualByComparingTo(penaltyAmount)

        // 패널티 2건이 서로 다른 정산에 하나씩 붙는다
        val penalties = noShowPenaltyRepository.findAll().sortedBy { it.id }
        assertThat(penalties.map { it.status })
            .containsExactly(NoShowPenaltyStatus.APPLIED, NoShowPenaltyStatus.APPLIED)
        assertThat(penalties.map { it.settlement!!.id })
            .containsExactly(firstSettlement.id, secondSettlement.id)

        // 세 번째 정산에는 더 이상 차감할 패널티가 없다
        val thirdSettlement = settlementService.createSettlement(
            payoutAmount, saveApplication(client, escort, ApplicationStatus.ACCEPTED), escort, LocalDate.now()
        )
        assertThat(thirdSettlement.penaltyAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 다른 동행인의 패널티는 내 정산에서 차감되지 않는다")
    fun otherEscortPenaltyNotApplied() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())
        val otherEscort = userRepository.save(createEscort("02"))

        // 패널티는 다른 동행인에게만 있다
        noShowPenaltyService.noShow(saveApplication(client, otherEscort))
        em.flush()

        val settlement = settlementService.createSettlement(
            payoutAmount, saveApplication(client, escort, ApplicationStatus.ACCEPTED), escort, LocalDate.now()
        )
        em.flush()
        em.clear()

        assertThat(settlement.penaltyAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(settlement.payoutAmount).isEqualByComparingTo(baseAmount)

        // 상대방 패널티는 그대로 남아 있다
        val penalty = noShowPenaltyRepository.findAll().first()
        assertThat(penalty.status).isEqualTo(NoShowPenaltyStatus.PENDING)
        assertThat(penalty.settlement).isNull()
    }

    // 패널티 1건을 정산에 적용해 APPLIED 로 만든다.
    private fun applyPenaltyThroughSettlement(client: User, escort: User): NoShowPenalty {
        noShowPenaltyService.noShow(saveApplication(client, escort))
        em.flush()
        settlementService.createSettlement(
            payoutAmount, saveApplication(client, escort, ApplicationStatus.ACCEPTED), escort, LocalDate.now()
        )
        em.flush()
        return noShowPenaltyRepository.findAll().first { it.application.escort.id == escort.id }
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 관리자 집계는 오늘 적용된 패널티를 오늘 구간으로 조회할 수 있다")
    fun getAllIncludesToday() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())
        val applied = applyPenaltyThroughSettlement(client, escort)

        // 적용 시각이 오늘 00:00 이 아니라 '지금'이라, 종료일을 그대로 쓰면 당일 건이 빠진다
        assertThat(noShowPenaltyService.getAll(LocalDate.now(), LocalDate.now()).map { it.id })
            .contains(applied.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 관리자 집계는 기간을 생략하면 최근 7일을 조회한다")
    fun getAllWithDefaultPeriod() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())
        val applied = applyPenaltyThroughSettlement(client, escort)

        assertThat(noShowPenaltyService.getAll(null, null).map { it.id }).contains(applied.id)
        // 7일 전까지만 보므로 더 과거 구간으로는 잡히지 않는다
        assertThat(
            noShowPenaltyService.getAll(LocalDate.now().minusDays(30), LocalDate.now().minusDays(29)).map { it.id }
        ).doesNotContain(applied.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 한 정산에 패널티 2건을 적용할 수 없다")
    fun onePenaltyPerSettlement() {
        val client = userRepository.save(createClient())
        val escort = userRepository.save(createEscort())
        val escortId = escort.id

        val first = saveApplication(client, escort)
        noShowPenaltyService.noShow(first)
        noShowPenaltyService.noShow(saveApplication(client, escort))
        em.flush()

        val penalties = noShowPenaltyRepository.findByEscortIdAndStatus(escortId)
        val settlement = settlementRepository.save(createSettlement(first, escort))
        noShowPenaltyService.applyPenalty(escortId, penaltyAmount, baseAmount, settlement, penalties[0])
        em.flush()

        // 정산 1건에 패널티는 하나만 붙어야 한다 (settlement_id unique)
        // flush 는 리포지토리로 호출해야 Hibernate 예외가 스프링 예외로 변환된다
        assertThatThrownBy {
            noShowPenaltyService.applyPenalty(escortId, penaltyAmount, baseAmount, settlement, penalties[1])
            noShowPenaltyRepository.flush()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
