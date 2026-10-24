package com.back.nbe12142team06.domain.settlement.repository

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * SettlementRepository 쿼리의 조건절·정렬 검증.
 * 조회 대상이 동행인/정산 단위라서 다른 테스트가 남긴 데이터와 섞이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettlementRepositoryTest {

    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var emf: EntityManagerFactory

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var otherEscort: User

    @BeforeEach
    fun setUp() {
        client = createUser("settleClient", Role.CLIENT, "010-3333-0001")
        escort = createUser("settleEscort", Role.ESCORT, "010-3333-0002")
        otherEscort = createUser("settleOther", Role.ESCORT, "010-3333-0003")
        em.persist(client)
        em.persist(escort)
        em.persist(otherEscort)

        // 계좌 조회 쿼리가 EscortProfile 과 조인하므로 프로필이 있어야 한다
        em.persist(EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-123"))
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User {
        return User(
            username, "password1!", "$username@test.test",
            if (role == Role.ESCORT) "동행매니저" else "의뢰인",
            role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
        )
    }

    private fun persistSettlement(
        status: SettlementStatus,
        settledDate: LocalDate = LocalDate.now(),
        owner: User = escort,
        payoutAmount: BigDecimal = BigDecimal.valueOf(135_000)
    ): Settlement {
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
            .status(ApplicationStatus.ACCEPTED)
            .acceptedPostId(post.id)
            .build()
        em.persist(application)

        val settlement = Settlement(
            id = null,
            payoutAmount = payoutAmount,
            platformFee = BigDecimal.valueOf(15_000),
            penaltyAmount = BigDecimal.ZERO,
            settlementStatus = status,
            settledDate = settledDate,
            application = application,
            escort = owner
        )
        em.persist(settlement)
        return settlement
    }

    @Test
    @DisplayName("[SettlementRepository] 계좌 조회는 정산 전·실패·정산 중만 대상으로 한다")
    fun findByIdAndState() {
        val pending = persistSettlement(SettlementStatus.PENDING)
        val failed = persistSettlement(SettlementStatus.FAILED)
        val processing = persistSettlement(SettlementStatus.PROCESSING)
        val completed = persistSettlement(SettlementStatus.COMPLETED)
        em.flush()
        em.clear()

        assertThat(settlementRepository.findByIdAndState(escort.id, pending.requiredId)).isNotNull()
        assertThat(settlementRepository.findByIdAndState(escort.id, failed.requiredId)).isNotNull()
        assertThat(settlementRepository.findByIdAndState(escort.id, processing.requiredId)).isNotNull()
        // 정산 완료 건은 조회되지 않는다
        assertThat(settlementRepository.findByIdAndState(escort.id, completed.requiredId)).isNull()
    }

    @Test
    @DisplayName("[SettlementRepository] 계좌 조회는 본인 정산만 가져온다")
    fun findByIdAndStateWithOtherEscort() {
        val settlement = persistSettlement(SettlementStatus.PENDING)
        em.flush()
        em.clear()

        val accountDto = settlementRepository.findByIdAndState(escort.id, settlement.requiredId)

        assertThat(accountDto).isNotNull()
        assertThat(accountDto!!.accountNumber).isEqualTo("123-000000-123")
        assertThat(accountDto.name).isEqualTo("동행매니저")
        assertThat(accountDto.payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(135_000))
        // 다른 동행인 id 로는 조회되지 않는다
        assertThat(settlementRepository.findByIdAndState(otherEscort.id, settlement.requiredId)).isNull()
    }

    @Test
    @DisplayName("[SettlementRepository] 관리자용 계좌 조회는 동행인 id 없이 상태만 본다")
    fun findByIdAndStateForAdmin() {
        val processing = persistSettlement(SettlementStatus.PROCESSING)
        val completed = persistSettlement(SettlementStatus.COMPLETED)
        em.flush()
        em.clear()

        assertThat(settlementRepository.findByIdAndState(processing.requiredId)).isNotNull()
        assertThat(settlementRepository.findByIdAndState(completed.requiredId)).isNull()
    }

    @Test
    @DisplayName("[SettlementRepository] 스케줄러 대상은 정산일이 지난 정산 전·실패 건이다")
    fun findAllByStatusAndDate() {
        val pendingToday = persistSettlement(SettlementStatus.PENDING, LocalDate.now())
        val failedYesterday = persistSettlement(SettlementStatus.FAILED, LocalDate.now().minusDays(1))
        // 정산일이 아직 오지 않은 건과 이미 처리 중·완료된 건은 대상이 아니다
        persistSettlement(SettlementStatus.PENDING, LocalDate.now().plusDays(1))
        persistSettlement(SettlementStatus.PROCESSING, LocalDate.now())
        persistSettlement(SettlementStatus.COMPLETED, LocalDate.now())
        em.flush()
        em.clear()

        val result = settlementRepository.findAllByStatusAndDate()

        assertThat(result.map { it.id })
            .containsExactlyInAnyOrder(pendingToday.requiredId, failedYesterday.requiredId)
    }

    @Test
    @DisplayName("[SettlementRepository] 사용자 정산 목록은 정산일 구간으로 조회한다")
    fun findIdsByUserAndDate() {
        val inRange = persistSettlement(SettlementStatus.COMPLETED, LocalDate.now().minusDays(3))
        persistSettlement(SettlementStatus.COMPLETED, LocalDate.now().minusDays(30))
        persistSettlement(SettlementStatus.COMPLETED, LocalDate.now().minusDays(3), owner = otherEscort)
        em.flush()
        em.clear()

        val idPage = settlementRepository.findIdsByUserAndDate(
            escort.id, LocalDate.now().minusDays(7), LocalDate.now(), PageRequest.of(0, 10)
        )

        assertThat(idPage.content).containsExactly(inRange.requiredId)

        // id 목록으로 다시 조회할 때 공고·동행인을 함께 읽는다
        val settlements = settlementRepository.findAllByIds(idPage.content)
        assertThat(settlements).hasSize(1)
        assertThat(settlements.first().application.post!!.title).isEqualTo("정형외과 동행 구합니다")
    }

    @Test
    @DisplayName("[SettlementRepository] 정산 중 선점은 정산 전·실패 건만 바꾼다")
    fun updateProcessing() {
        val pending = persistSettlement(SettlementStatus.PENDING)
        val processing = persistSettlement(SettlementStatus.PROCESSING)
        em.flush()
        em.clear()

        assertThat(settlementRepository.updateProcessing(pending.requiredId)).isEqualTo(1)
        assertThat(settlementRepository.updateProcessing(processing.requiredId)).isEqualTo(0)
        assertThat(settlementRepository.findById(pending.requiredId).get().settlementStatus)
            .isEqualTo(SettlementStatus.PROCESSING)
    }

    @Test
    @DisplayName("[SettlementRepository] 정산 완료가 아닌 건이 있으면 미정산으로 본다")
    fun existsByEscortIdAndSettlementStatusNot() {
        persistSettlement(SettlementStatus.COMPLETED)
        em.flush()
        em.clear()

        assertThat(
            settlementRepository.existsByEscortIdAndSettlementStatusNot(escort.id, SettlementStatus.COMPLETED)
        ).isFalse()

        persistSettlement(SettlementStatus.PENDING)
        em.flush()
        em.clear()

        assertThat(
            settlementRepository.existsByEscortIdAndSettlementStatusNot(escort.id, SettlementStatus.COMPLETED)
        ).isTrue()
    }

    @Test
    @DisplayName("[SettlementRepository] 단건 조회는 동행인을 함께 읽어 추가 쿼리가 없다")
    fun fetchJoinById() {
        val settlement = persistSettlement(SettlementStatus.PROCESSING)
        em.flush()
        em.clear()

        val statistics = emf.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        val found = settlementRepository.fetchJoinById(settlement.requiredId)!!
        val queryCountAfterFind = statistics.prepareStatementCount

        // 지연 로딩이면 여기서 동행인 조회 쿼리가 더 나간다
        assertThat(found.escort.name).isEqualTo("동행매니저")

        assertThat(queryCountAfterFind).isEqualTo(1)
        assertThat(statistics.prepareStatementCount).isEqualTo(1)
    }
}
