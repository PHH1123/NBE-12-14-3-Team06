package com.back.nbe12142team06.domain.payment.service

import com.back.nbe12142team06.domain.payment.dto.PaymentAdminResponse
import com.back.nbe12142team06.domain.payment.dto.PaymentAnalyticsResponse
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceAdminTest {

    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var paymentService: PaymentService
    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository

    private fun createPayment(
        post: Post,
        status: PaymentStatus,
        approvedAt: LocalDateTime,
        hours: BigDecimal,
        mulBalanceAmount: BigDecimal = BigDecimal.ONE
    ): Payment {
        val hourlyPaySnapshot = 15_000
        val amount = hours.multiply(BigDecimal.valueOf(hourlyPaySnapshot.toLong()))

        return Payment(
            id = null,
            amount = amount,
            hourlyPaySnapshot = hourlyPaySnapshot,
            hours = hours,
            post = post,
            method = "CARD",
            paymentStatus = status,
            orderId = UUID.randomUUID().toString(),
            paymentKey = UUID.randomUUID().toString(),
            approvedAt = approvedAt,
            balanceAmount = mulBalanceAmount.multiply(amount)
        )
    }

    private fun createPost(client: User, hour: Long = 10): Post {
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
            .hourlyPay(15_000)
            .recruitStartAt(LocalDateTime.now().plusDays(1))
            .recruitEndAt(LocalDateTime.now().plusDays(6))
            .escortStartAt(LocalDateTime.now().plusDays(7))
            .escortEndAt(LocalDateTime.now().plusDays(7).plusHours(hour))
            .client(client)
            .build()
    }

    private fun createClient(): User {
        return User(
            "aclient01", "password1!", "aclient01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1239", "서울"
        )
    }

    @Test
    @DisplayName("[PaymentService] 관리자 일별 결제 정보 조회")
    fun dailyPayment() {
        val client = userRepository.save(createClient())
        val post = postRepository.save(createPost(client))

        val payment1 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(8), BigDecimal.TEN)
        val payment2 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(7), BigDecimal.ONE)
        val payment3 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(6), BigDecimal.TWO)
        val payment4 = createPayment(
            post, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(6), BigDecimal.valueOf(5L)
        )
        val payment5 = createPayment(
            post, PaymentStatus.DONE, LocalDateTime.now().minusDays(5), BigDecimal.valueOf(3L)
        )

        paymentRepository.saveAll(listOf(payment1, payment2, payment3, payment4, payment5))

        val result = paymentService.getDailyPaymentAnalytics(LocalDate.now().minusDays(7), LocalDate.now())

        // 조회 기간(7일 전 ~ 오늘)을 벗어난 payment1 은 빠지고, 같은 날짜인 payment3·payment4 는 합산된다
        assertThat(result[0].paidAmount).isEqualByComparingTo(BigDecimal.valueOf(15_000L))
        assertThat(result[1].paidAmount).isEqualByComparingTo(BigDecimal.valueOf(105_000L))
        assertThat(result[2].paidAmount).isEqualByComparingTo(BigDecimal.valueOf(45_000L))
    }

    @Test
    @DisplayName("[PaymentService] 관리자 기간별 결제 및 환불 금액 조회")
    fun paymentByPeriod() {
        val client = userRepository.save(createClient())
        val post = postRepository.save(createPost(client))

        // 150_000 (조회 기간 밖)
        val payment1 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(8), BigDecimal.TEN)
        // 15_000
        val payment2 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(7), BigDecimal.ONE)
        // 30_000
        val payment3 = createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(6), BigDecimal.TWO)
        // 75_000 - 37_500 = 37_500
        val payment4 = createPayment(
            post, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(6),
            BigDecimal.valueOf(5L), BigDecimal.valueOf(0.5)
        )
        // 45_000 - 9_000 = 36_000
        val payment5 = createPayment(
            post, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(5),
            BigDecimal.valueOf(3L), BigDecimal.valueOf(0.8)
        )

        paymentRepository.saveAll(listOf(payment1, payment2, payment3, payment4, payment5))

        val payments = paymentService.getPaymentAnalyticsByPeriod(LocalDate.now().minusDays(7), LocalDate.now())
        val result = PaymentAnalyticsResponse(payments)

        assertThat(result.paidAmount).isEqualTo(118_500L)
        assertThat(result.canceledAmount).isEqualTo(46_500L)
        assertThat(result.counts).isEqualTo(4L)
    }

    @Test
    @DisplayName("[PaymentService] 관리자 기간별 결제 목록 조회")
    fun paymentList() {
        val client = userRepository.save(createClient())

        val post1 = postRepository.save(createPost(client, 8))
        val post2 = postRepository.save(createPost(client, 1))
        val post3 = postRepository.save(createPost(client, 2))
        val post4 = postRepository.save(createPost(client, 5))
        val post5 = postRepository.save(createPost(client, 3))

        val payment1 = createPayment(post1, PaymentStatus.DONE, LocalDateTime.now().minusDays(8), BigDecimal.TEN)
        val payment2 = createPayment(post2, PaymentStatus.DONE, LocalDateTime.now().minusDays(7), BigDecimal.ONE)
        val payment3 = createPayment(post3, PaymentStatus.DONE, LocalDateTime.now().minusDays(6), BigDecimal.TWO)
        val payment4 = createPayment(
            post4, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(6),
            BigDecimal.valueOf(5L), BigDecimal.valueOf(0.5)
        )
        val payment5 = createPayment(
            post5, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(5),
            BigDecimal.valueOf(3L), BigDecimal.valueOf(0.8)
        )

        paymentRepository.saveAll(listOf(payment1, payment2, payment3, payment4, payment5))

        val pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id")

        val paymentPage = paymentService.getPaymentsForAdmin(LocalDate.now().minusDays(7), LocalDate.now(), pageable)
        val result = paymentPage.map { PaymentAdminResponse(it) }.content

        assertThat(result).hasSize(4)
        assertThat(result.first().amount).isEqualTo(45_000)
        assertThat(result.first().postId).isEqualTo(post5.id)
        assertThat(result.last().amount).isEqualTo(15_000)
        assertThat(result.last().postId).isEqualTo(post2.id)
    }

    @Test
    @DisplayName("[PaymentService] 관리자 결제 상태 변경")
    fun updateStatus() {
        val client = userRepository.save(createClient())
        val post = postRepository.save(createPost(client, 8))

        val payment = paymentRepository.save(
            createPayment(post, PaymentStatus.DONE, LocalDateTime.now().minusDays(8), BigDecimal.TEN)
        )

        paymentService.updateStatusForAdmin(payment.requiredId, PaymentStatus.CANCELED)

        em.flush()
        em.clear()

        val updatedPayment = paymentService.findById(client.id, payment.requiredId)

        assertThat(updatedPayment.paymentStatus).isEqualTo(PaymentStatus.CANCELED)
    }
}
