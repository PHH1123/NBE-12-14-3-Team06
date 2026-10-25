package com.back.nbe12142team06.domain.penalty.repository

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenaltyStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * NoShowPenaltyRepository 쿼리의 조건절·정렬 검증.
 * 조회 대상이 동행인/상태 단위라서 다른 테스트가 남긴 데이터와 섞이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoShowPenaltyRepositoryTest {

    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository
    @field:Autowired private lateinit var em: EntityManager

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var otherEscort: User

    @BeforeEach
    fun setUp() {
        client = createUser("penaltyClient", Role.CLIENT, "010-4444-0001")
        escort = createUser("penaltyEscort", Role.ESCORT, "010-4444-0002")
        otherEscort = createUser("penaltyOther", Role.ESCORT, "010-4444-0003")
        em.persist(client)
        em.persist(escort)
        em.persist(otherEscort)
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User = User(
        username, "password1!", "$username@test.test",
        if (role == Role.ESCORT) "동행매니저" else "의뢰인",
        role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
    )

    // 노쇼 지원 하나를 만들어 패널티를 붙인다. (의뢰인 - 공고 - 동행 매니저 - 지원 - 패널티)
    private fun persistPenalty(owner: User = escort): NoShowPenalty {
        val post = Post.builder()
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
            .hourlyPay(15_000)
            .recruitStartAt(LocalDateTime.now().minusDays(10))
            .recruitEndAt(LocalDateTime.now().minusDays(5))
            .escortStartAt(LocalDateTime.now().minusDays(3))
            .escortEndAt(LocalDateTime.now().minusDays(3).plusHours(10))
            .postStatus(PostStatus.COMPLETED)
            .client(client)
            .build()
        em.persist(post)

        val application = Application.builder()
            .post(post)
            .escort(owner)
            .status(ApplicationStatus.NO_SHOW)
            .acceptedPostId(post.id)
            .build()
        em.persist(application)

        val penalty = NoShowPenalty(application = application)
        em.persist(penalty)
        return penalty
    }

    // 패널티를 정산에 적용해 APPLIED 로 만든다.
    private fun applyToSettlement(
        penalty: NoShowPenalty,
        penaltyAmount: BigDecimal = BigDecimal.valueOf(13_500),
        baseAmount: BigDecimal = BigDecimal.valueOf(135_000)
    ): Settlement {
        val settlement = Settlement(
            id = null,
            payoutAmount = baseAmount.subtract(penaltyAmount),
            platformFee = BigDecimal.valueOf(15_000),
            penaltyAmount = penaltyAmount,
            settlementStatus = SettlementStatus.PENDING,
            settledDate = LocalDate.now(),
            application = penalty.application,
            escort = penalty.application.escort!!
        )
        em.persist(settlement)
        penalty.updateNoShow(penaltyAmount, baseAmount, settlement)
        return settlement
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 적용 대상 조회는 PENDING 패널티만 가져온다")
    fun findByEscortIdAndStatusOnlyPending() {
        val pending = persistPenalty()
        applyToSettlement(persistPenalty())
        em.flush()
        em.clear()

        val result = noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)

        assertThat(result.map { it.id }).containsExactly(pending.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 적용 대상 조회는 본인 패널티만 가져온다")
    fun findByEscortIdAndStatusWithOtherEscort() {
        val mine = persistPenalty()
        val others = persistPenalty(owner = otherEscort)
        em.flush()
        em.clear()

        // 다른 동행인의 패널티가 내 정산에서 차감되면 안 된다
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(escort.id).map { it.id })
            .containsExactly(mine.id)
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(otherEscort.id).map { it.id })
            .containsExactly(others.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 적용 대상이 여러 건이면 오래된 패널티가 먼저 나온다")
    fun findByEscortIdAndStatusOrderedById() {
        val first = persistPenalty()
        val second = persistPenalty()
        val third = persistPenalty()
        em.flush()
        em.clear()

        val result = noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)

        assertThat(result.map { it.id }).containsExactly(first.id, second.id, third.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 관리자 집계는 APPLIED 패널티만 가져온다")
    fun findAllByStatusAndDateOnlyApplied() {
        persistPenalty()
        val applied = persistPenalty()
        applyToSettlement(applied)
        em.flush()
        em.clear()

        // 구간은 하루 단위로 넘긴다 (생성 시각이 '지금'이라 now..now 로는 아무것도 걸리지 않는다)
        val result = noShowPenaltyRepository.findAllByStatusAndDate(
            LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()
        )

        assertThat(result.map { it.id }).contains(applied.id)
        assertThat(result.map { it.status }).containsOnly(NoShowPenaltyStatus.APPLIED)
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 관리자 집계는 구간 밖의 패널티를 가져오지 않는다")
    fun findAllByStatusAndDateOutOfRange() {
        val applied = persistPenalty()
        applyToSettlement(applied)
        em.flush()
        em.clear()

        val result = noShowPenaltyRepository.findAllByStatusAndDate(
            LocalDate.now().minusDays(10).atStartOfDay(), LocalDate.now().minusDays(9).atStartOfDay()
        )

        assertThat(result.map { it.id }).doesNotContain(applied.id)
    }

    @Test
    @DisplayName("[NoShowPenaltyRepository] 패널티 금액은 원 단위로만 저장된다")
    fun penaltyAmountStoredInWon() {
        val penalty = persistPenalty()
        // 서비스는 setScale(0, DOWN) 으로 맞춰서 넘기지만, 컬럼 자체도 원 단위(scale 0)여야 한다
        applyToSettlement(penalty, BigDecimal("13500.6"), BigDecimal("135000.4"))
        em.flush()
        em.clear()

        val stored = noShowPenaltyRepository.findById(penalty.id!!).orElseThrow()

        assertThat(stored.amount!!.scale()).isEqualTo(0)
        assertThat(stored.baseAmount!!.scale()).isEqualTo(0)
    }
}
