package com.back.nbe12142team06.domain.payment.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.InvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Payment 엔티티의 상태 변경·금액 계산 테스트.
 * 상태 전이 규칙 자체(어떤 전이가 허용되는지)는 PaymentStatusTransitionTest 에서 전수 검증한다.
 */
class PaymentTest {

    private val hourlyPay = 15_000
    private val hours: BigDecimal = BigDecimal.TEN
    private val amount: BigDecimal = BigDecimal.valueOf(150_000)

    private fun createPost(): Post {
        val client = User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
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
            hourlyPay = hourlyPay,
            recruitStartAt = LocalDateTime.now().plusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(6),
            escortStartAt = LocalDateTime.now().plusDays(7),
            escortEndAt = LocalDateTime.now().plusDays(7).plusHours(10)
        )
    }

    private fun createPayment(
        status: PaymentStatus,
        id: Long? = 1L,
        paymentKey: String? = "tossPaymentKey",
        balanceAmount: BigDecimal = amount
    ): Payment {
        val isReady = status == PaymentStatus.READY

        return Payment(
            id = id,
            amount = amount,
            hourlyPaySnapshot = hourlyPay,
            hours = hours,
            post = createPost(),
            method = if (isReady) null else "CARD",
            paymentStatus = status,
            orderId = if (isReady) null else "tossOrderId",
            paymentKey = if (isReady) null else paymentKey,
            approvedAt = if (isReady) null else LocalDateTime.now().minusDays(2),
            balanceAmount = if (isReady) BigDecimal.ZERO else balanceAmount
        )
    }

    @Test
    @DisplayName("[Payment] 허용되지 않은 상태 전이는 거부한다")
    fun statusUpdateRejectsInvalidTransition() {
        val payment = createPayment(PaymentStatus.READY)

        assertThatThrownBy { payment.statusUpdate(PaymentStatus.DONE) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("'결제 대기' 상태의 결제는 '결제 완료' 상태로 바꿀 수 없습니다.")

        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.READY)
    }

    @Test
    @DisplayName("[Payment] 결제 승인 시 승인 정보가 채워지고 취소 가능 금액이 결제 금액이 된다")
    fun approvePayment() {
        val payment = createPayment(PaymentStatus.IN_PROGRESS, balanceAmount = BigDecimal.ZERO)

        payment.approvePayment("newOrderId", "newPaymentKey", "계좌이체")

        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.DONE)
        assertThat(payment.orderId).isEqualTo("newOrderId")
        assertThat(payment.paymentKey).isEqualTo("newPaymentKey")
        assertThat(payment.method).isEqualTo("계좌이체")
        assertThat(payment.balanceAmount).isEqualByComparingTo(amount)
        assertThat(payment.approvedAt).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
    }

    @Test
    @DisplayName("[Payment] 결제 취소 시 취소 정보가 채워지고 같은 금액의 미결제 건을 새로 만든다")
    fun cancelPayment() {
        val payment = createPayment(PaymentStatus.DONE)

        val newPayment = payment.cancelPayment("의뢰인 취소 요청")

        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.CANCELED)
        assertThat(payment.balanceAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(payment.cancelReason).isEqualTo("의뢰인 취소 요청")
        assertThat(payment.canceledAt).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))

        // 재결제를 위해 같은 조건의 READY 결제를 돌려준다
        assertThat(newPayment.paymentStatus).isEqualTo(PaymentStatus.READY)
        assertThat(newPayment.amount).isEqualByComparingTo(amount)
        assertThat(newPayment.hours).isEqualByComparingTo(hours)
        assertThat(newPayment.hourlyPaySnapshot).isEqualTo(hourlyPay)
        assertThat(newPayment.post).isSameAs(payment.post)
        assertThat(newPayment.paymentKey).isNull()
        assertThat(newPayment.approvedAt).isNull()
    }

    @Test
    @DisplayName("[Payment] 부분 취소는 결제 금액은 그대로 두고 취소 가능 금액만 줄인다")
    fun cancelPartialPayment() {
        val payment = createPayment(PaymentStatus.DONE)

        payment.cancelPartialPayment("동행 시간 단축", BigDecimal.valueOf(50_000))

        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.PARTIAL_CANCELED)
        assertThat(payment.balanceAmount).isEqualByComparingTo(BigDecimal.valueOf(100_000))
        assertThat(payment.amount).isEqualByComparingTo(amount)
        assertThat(payment.cancelReason).isEqualTo("동행 시간 단축")
    }

    @Test
    @DisplayName("[Payment] 부분 취소된 결제는 다시 부분 취소할 수 없다")
    fun cancelPartialPaymentTwice() {
        val payment = createPayment(PaymentStatus.DONE)
        payment.cancelPartialPayment("1차 단축", BigDecimal.valueOf(50_000))

        // PARTIAL_CANCELED 는 종료 상태라 추가 부분 취소가 막힌다 (PaymentStatus.canTransitionTo)
        assertThatThrownBy { payment.cancelPartialPayment("2차 단축", BigDecimal.valueOf(10_000)) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("'결제 부분 취소' 상태의 결제는 '결제 부분 취소' 상태로 바꿀 수 없습니다.")
    }

    @Test
    @DisplayName("[Payment] 결제 금액 검증 - 소수점 표기가 달라도 금액이 같으면 통과")
    fun verifyAmountSuccess() {
        val payment = createPayment(PaymentStatus.READY)

        assertThat(payment.verifyAmount("150000")).isTrue()
        assertThat(payment.verifyAmount("150000.00")).isTrue()
    }

    @Test
    @DisplayName("[Payment] 결제 금액 검증 - 금액이 다르면 거부")
    fun verifyAmountFailed() {
        val payment = createPayment(PaymentStatus.READY)

        assertThatThrownBy { payment.verifyAmount("149999") }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 금액 정보가 유효하지 않습니다.")
    }

    @Test
    @DisplayName("[Payment] 공고 수정 시 금액과 동행 시간을 함께 갱신한다")
    fun update() {
        val payment = createPayment(PaymentStatus.READY)

        payment.update(60_000, BigDecimal.valueOf(4))

        assertThat(payment.amount).isEqualByComparingTo(BigDecimal.valueOf(60_000))
        assertThat(payment.hours).isEqualByComparingTo(BigDecimal.valueOf(4))
    }

    @Test
    @DisplayName("[Payment] 취소 시각·정산 연결을 갱신한다")
    fun updateCanceledAtAndSettlement() {
        val payment = createPayment(PaymentStatus.DONE)
        val escort = User(
            "escort01", "password1!", "escort01@email.com", "동행매니저",
            Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
        )
        val settlement = Settlement(
            payoutAmount = amount,
            settledDate = LocalDate.now(),
            application = Application(createPost(), escort),
            escort = escort
        )

        payment.updateCanceledAt()
        payment.updateSettlement(settlement)

        assertThat(payment.canceledAt).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
        assertThat(payment.settlement).isSameAs(settlement)
    }

    @Test
    @DisplayName("[Payment] 저장되지 않아 id가 없으면 requiredId 는 예외를 던진다")
    fun requiredIdWithoutId() {
        val payment = createPayment(PaymentStatus.READY, id = null)

        assertThatThrownBy { payment.requiredId }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("ID가 NULL입니다.")
    }
}
