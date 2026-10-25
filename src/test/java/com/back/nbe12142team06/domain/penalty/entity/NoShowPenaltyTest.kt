package com.back.nbe12142team06.domain.penalty.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.InvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * NoShowPenalty 엔티티의 기본값과 상태 전이 테스트.
 * DB·Spring 컨텍스트 없이 엔티티만 두고 돌린다. (SettlementTest / PaymentTest 와 같은 층)
 */
class NoShowPenaltyTest {

    private val baseAmount: BigDecimal = BigDecimal.valueOf(135_000)
    private val penaltyAmount: BigDecimal = BigDecimal.valueOf(13_500)

    private fun createEscort(): User = User(
        "escort01", "password1!", "escort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
    )

    private fun createPost(client: User): Post = Post.builder()
        .id(3L)
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
        .client(client)
        .build()

    private fun createApplication(escort: User): Application {
        val client = User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
        return Application.builder().id(5L).post(createPost(client)).escort(escort).build()
    }

    private fun createSettlement(application: Application, escort: User, id: Long): Settlement = Settlement(
        id = id,
        payoutAmount = baseAmount.subtract(penaltyAmount),
        platformFee = BigDecimal.valueOf(15_000),
        penaltyAmount = penaltyAmount,
        settlementStatus = SettlementStatus.PENDING,
        settledDate = LocalDate.now().plusDays(1),
        application = application,
        escort = escort
    )

    @Test
    @DisplayName("[NoShowPenalty] 생성 직후에는 적용 전(PENDING)이고 금액·정산이 비어 있다")
    fun createdAsPending() {
        val escort = createEscort()
        val penalty = NoShowPenalty(application = createApplication(escort))

        assertThat(penalty.status).isEqualTo(NoShowPenaltyStatus.PENDING)
        // 패널티 금액은 정산할 때 정해지므로 생성 시점에는 없다
        assertThat(penalty.amount).isNull()
        assertThat(penalty.baseAmount).isNull()
        assertThat(penalty.settlement).isNull()
        assertThat(penalty.application.id).isEqualTo(5L)
    }

    @Test
    @DisplayName("[NoShowPenalty] 패널티를 적용하면 금액·정산이 기록되고 APPLIED 로 바뀐다")
    fun updateNoShowApplies() {
        val escort = createEscort()
        val application = createApplication(escort)
        val penalty = NoShowPenalty(application = application)
        val settlement = createSettlement(application, escort, 11L)

        penalty.updateNoShow(penaltyAmount, baseAmount, settlement)

        assertThat(penalty.status).isEqualTo(NoShowPenaltyStatus.APPLIED)
        assertThat(penalty.amount).isEqualByComparingTo(penaltyAmount)
        // 기준 금액은 패널티를 떼기 전의 정산 금액이다
        assertThat(penalty.baseAmount).isEqualByComparingTo(baseAmount)
        assertThat(penalty.settlement).isSameAs(settlement)
    }

    @Test
    @DisplayName("[NoShowPenalty] 이미 적용된 패널티는 다시 적용할 수 없다")
    fun updateNoShowRejectedWhenAlreadyApplied() {
        val escort = createEscort()
        val application = createApplication(escort)
        val penalty = NoShowPenalty(application = application)
        val firstSettlement = createSettlement(application, escort, 11L)
        val secondSettlement = createSettlement(application, escort, 12L)

        penalty.updateNoShow(penaltyAmount, baseAmount, firstSettlement)

        // 한 번 적용된 패널티가 다음 정산에서 또 차감되면 안 되므로 엔티티에서 막아야 한다
        assertThatThrownBy { penalty.updateNoShow(penaltyAmount, baseAmount, secondSettlement) }
            .isInstanceOf(InvalidException::class.java)

        // 첫 적용 기록은 그대로 남는다
        assertThat(penalty.settlement).isSameAs(firstSettlement)
        assertThat(penalty.amount).isEqualByComparingTo(penaltyAmount)
    }
}
