package com.back.nbe12142team06.domain.payment.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * PaymentService 는 트랜잭션을 걸지 않고 PaymentPersistenceService 가 단계마다 커밋한다.
 * 그 커밋 경계를 검증하는 테스트라서 테스트에 @Transactional 을 걸지 않는다.
 * (테스트가 트랜잭션을 쥐고 있으면 내부 @Transactional 이 거기에 참여해 커밋이 일어나지 않는다)
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentNotTransactionServiceTest {

    @field:Autowired private lateinit var paymentService: PaymentService
    @field:Autowired private lateinit var paymentPersistenceService: PaymentPersistenceService
    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:MockitoSpyBean private lateinit var tossPaymentClient: TossPaymentClient

    @field:Autowired private lateinit var databaseCleaner: DatabaseCleaner

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    private fun createPayment(post: Post, status: PaymentStatus): Payment {
        val hourlyPaySnapshot = 15_000
        val hours = BigDecimal.TEN
        val amount = hours.multiply(BigDecimal.valueOf(hourlyPaySnapshot.toLong()))
        // 결제 대기 상태에는 결제 수단·승인 정보가 아직 없다
        val isReady = status == PaymentStatus.READY

        return Payment(
            id = null,
            amount = amount,
            hourlyPaySnapshot = hourlyPaySnapshot,
            hours = hours,
            post = post,
            method = if (isReady) null else "CARD",
            paymentStatus = status,
            approvedAt = if (isReady) null else LocalDateTime.now().minusDays(2),
            balanceAmount = if (isReady) BigDecimal.ZERO else amount
        )
    }

    private fun createPost(client: User, hour: Long = 10): Post {
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
            recruitStartAt = LocalDateTime.now().plusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(6),
            escortStartAt = LocalDateTime.now().plusDays(7),
            escortEndAt = LocalDateTime.now().plusDays(7).plusHours(hour)
        )
    }

    private fun createClient(): User {
        return User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
    }

    private fun saveReadyPayment(): Payment {
        val client = userRepository.save(createClient())
        val post = postRepository.save(createPost(client))
        return paymentRepository.save(createPayment(post, PaymentStatus.READY))
    }

    @Test
    @DisplayName("[PaymentService] 승인 도중 IN_PROGRESS가 커밋되어 다른 트랜잭션에서도 보인다")
    fun confirmUpdateStatusIsCommitted() {
        val payment = saveReadyPayment()

        val updatedCount = paymentPersistenceService.confirmUpdateStatus(payment.requiredId)

        // 테스트에 트랜잭션이 없으므로 아래 조회는 새 트랜잭션에서 커밋된 상태를 읽는다
        val reloaded = paymentRepository.findById(payment.requiredId).get()

        assertThat(updatedCount).isEqualTo(1)
        assertThat(reloaded.paymentStatus).isEqualTo(PaymentStatus.IN_PROGRESS)
    }

    @Test
    @DisplayName("[PaymentService] 이미 IN_PROGRESS인 결제는 0건 반환 - 중복 승인 차단")
    fun confirmUpdateStatusBlocksDuplicate() {
        val payment = saveReadyPayment()

        val firstCount = paymentPersistenceService.confirmUpdateStatus(payment.requiredId)
        val secondCount = paymentPersistenceService.confirmUpdateStatus(payment.requiredId)

        assertThat(firstCount).isEqualTo(1)
        assertThat(secondCount).isEqualTo(0)
    }

    @Test
    @DisplayName("[PaymentService] 결제 승인 실패 시 결제 데이터 하드 삭제가 커밋된다")
    fun paymentDeletePaymentWithConfirmFailed() {
        val payment = saveReadyPayment()
        val paymentId = payment.requiredId
        val userId = payment.post.client.id

        doThrow(InternalServerErrorException(43, "토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다."))
            .whenever(tossPaymentClient).callApiConfirm(any(), any(), any())

        assertThatThrownBy {
            paymentService.confirm(
                PaymentConfirmRequest("tempPaymentKey", "tempOrderId", payment.amount.toString()),
                paymentId, userId
            )
        }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다.")

        // 예외가 그대로 전파됐지만 삭제는 자기 트랜잭션에서 커밋되어 남아 있어야 한다
        assertThat(paymentRepository.findById(paymentId)).isEmpty()
    }
}
