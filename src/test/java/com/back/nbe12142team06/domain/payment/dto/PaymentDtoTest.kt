package com.back.nbe12142team06.domain.payment.dto

import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 결제 응답 DTO 의 매핑·집계 테스트. 엔티티만 있으면 되므로 DB 없이 돌린다.
 */
class PaymentDtoTest {

    private val hourlyPay = 15_000

    private fun createClient(): User {
        val client = User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(client, 7L)
        return client
    }

    private fun createPost(client: User): Post {
        return Post.builder()
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
            .hourlyPay(hourlyPay)
            .recruitStartAt(LocalDateTime.now().plusDays(1))
            .recruitEndAt(LocalDateTime.now().plusDays(6))
            .escortStartAt(LocalDateTime.now().plusDays(7))
            .escortEndAt(LocalDateTime.now().plusDays(7).plusHours(10))
            .client(client)
            .build()
    }

    private fun createPayment(
        post: Post,
        status: PaymentStatus,
        amount: BigDecimal,
        balanceAmount: BigDecimal
    ): Payment {
        return Payment(
            id = 11L,
            amount = amount,
            hourlyPaySnapshot = hourlyPay,
            hours = BigDecimal.TEN,
            post = post,
            method = "CARD",
            paymentStatus = status,
            orderId = "tossOrderId",
            paymentKey = "tossPaymentKey",
            approvedAt = LocalDateTime.now().minusDays(1),
            balanceAmount = balanceAmount
        )
    }

    @Test
    @DisplayName("[PaymentAnalyticsResponse] 결제 금액은 남은 금액 합, 환불 금액은 취소된 금액 합")
    fun analyticsResponse() {
        val post = createPost(createClient())
        val payments = listOf(
            // 전액 결제 유지
            createPayment(post, PaymentStatus.DONE, BigDecimal.valueOf(150_000), BigDecimal.valueOf(150_000)),
            // 75,000 중 37,500 환불
            createPayment(
                post, PaymentStatus.PARTIAL_CANCELED,
                BigDecimal.valueOf(75_000), BigDecimal.valueOf(37_500)
            ),
            // 45,000 중 9,000 환불
            createPayment(
                post, PaymentStatus.PARTIAL_CANCELED,
                BigDecimal.valueOf(45_000), BigDecimal.valueOf(36_000)
            )
        )

        val result = PaymentAnalyticsResponse(payments)

        assertThat(result.paidAmount).isEqualTo(223_500L)
        assertThat(result.canceledAmount).isEqualTo(46_500L)
        assertThat(result.counts).isEqualTo(3L)
    }

    @Test
    @DisplayName("[PaymentAnalyticsResponse] 결제가 없으면 모두 0")
    fun analyticsResponseEmpty() {
        val result = PaymentAnalyticsResponse(emptyList())

        assertThat(result.paidAmount).isEqualTo(0L)
        assertThat(result.canceledAmount).isEqualTo(0L)
        assertThat(result.counts).isEqualTo(0L)
    }

    @Test
    @DisplayName("[PaymentResponse] 엔티티를 사용자 응답으로 매핑")
    fun paymentResponse() {
        val post = createPost(createClient())
        val payment = createPayment(
            post, PaymentStatus.DONE, BigDecimal.valueOf(150_000), BigDecimal.valueOf(150_000)
        )

        val result = PaymentResponse(payment)

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.amount).isEqualTo(150_000)
        assertThat(result.hourlyPaySnapshot).isEqualTo(hourlyPay)
        assertThat(result.hours).isEqualByComparingTo(BigDecimal.TEN)
        assertThat(result.orderId).isEqualTo("tossOrderId")
        assertThat(result.paymentStatus).isEqualTo(PaymentStatus.DONE)
        assertThat(result.approvedAt).isEqualTo(payment.approvedAt)
        assertThat(result.canceledAt).isNull()
        assertThat(result.cancelReason).isNull()
        assertThat(result.postId).isEqualTo(3L)
    }

    @Test
    @DisplayName("[PaymentAdminResponse] 엔티티를 관리자 응답으로 매핑 - 공고·의뢰인 정보 포함")
    fun paymentAdminResponse() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(
            post, PaymentStatus.PARTIAL_CANCELED, BigDecimal.valueOf(150_000), BigDecimal.valueOf(100_000)
        )

        val result = PaymentAdminResponse(payment)

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.amount).isEqualTo(150_000)
        assertThat(result.balanceAmount).isEqualTo(100_000)
        assertThat(result.paymentStatus).isEqualTo(PaymentStatus.PARTIAL_CANCELED)
        assertThat(result.postId).isEqualTo(3L)
        assertThat(result.postTitle).isEqualTo("정형외과 동행 구합니다")
        assertThat(result.userId).isEqualTo(7L)
        assertThat(result.name).isEqualTo("의뢰인")
    }

    @Test
    @DisplayName("[PaymentConfirmResponse] 승인 요청 값을 그대로 응답으로 옮긴다")
    fun paymentConfirmResponse() {
        val request = PaymentConfirmRequest("pk_123", "order_456", "60000")

        val result = PaymentConfirmResponse(request)

        assertThat(result.paymentKey).isEqualTo("pk_123")
        assertThat(result.orderId).isEqualTo("order_456")
        assertThat(result.amount).isEqualTo("60000")
    }
}
