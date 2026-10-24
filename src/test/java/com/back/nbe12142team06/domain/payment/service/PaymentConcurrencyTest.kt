package com.back.nbe12142team06.domain.payment.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    @field:Autowired private lateinit var paymentService: PaymentService
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
            orderId = if (isReady) null else UUID.randomUUID().toString(),
            paymentKey = if (isReady) null else UUID.randomUUID().toString(),
            approvedAt = if (isReady) null else LocalDateTime.now().minusDays(2),
            balanceAmount = if (isReady) BigDecimal.ZERO else amount
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
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
    }

    @Test
    @DisplayName("[PaymentService] 결제 승인 2번 동시 요청")
    fun paymentConfirmConcurrency() {
        val savedClient = userRepository.save(createClient())
        val savedPost = postRepository.save(createPost(savedClient))
        val savedPayment = paymentRepository.save(createPayment(savedPost, PaymentStatus.READY))

        val request = PaymentConfirmRequest(
            "tempPaymentKey", "tempOrderId", savedPayment.amount.toString()
        )

        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", savedPayment.amount.toString())))
            .whenever(tossPaymentClient)
            .callApiConfirm(request.paymentKey, request.orderId, request.amount)

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(1)
        val failedCount = AtomicInteger()

        repeat(2) {
            executor.submit {
                latch.await()
                runCatching {
                    paymentService.confirm(request, savedPayment.requiredId, savedClient.id)
                }.onFailure { failedCount.incrementAndGet() }
            }
        }

        latch.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        // 승인 요청은 한 번만 나가고, 나머지 한 건은 예외로 끝나야 한다
        // (원래는 submit 안에서 예외가 그대로 묻혀서 승인 호출 횟수만 확인했다)
        verify(tossPaymentClient, times(1)).callApiConfirm(any(), any(), any())
        assertThat(failedCount.get()).isEqualTo(1)
    }
}
