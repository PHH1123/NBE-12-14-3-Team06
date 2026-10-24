package com.back.nbe12142team06.domain.payment.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.PaymentCancelRequest
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * PaymentService 의 검증·분기 로직 테스트.
 * DB 와 Spring 컨텍스트 없이 경계(리포지토리·외부 결제 API·알림·정산)만 대역으로 바꿔 돌린다.
 * 커밋 경계가 걸린 흐름은 PaymentNotTransactionServiceTest 에서 다룬다.
 */
class PaymentServiceTest {

    private val paymentRepository: PaymentRepository = mock()
    private val applicationRepository: ApplicationRepository = mock()
    private val tossPaymentClient: TossPaymentClient = mock()
    private val settlementService: SettlementService = mock()
    private val noticeService: NoticeService = mock()

    private val paymentPersistenceService =
        spy(PaymentPersistenceService(paymentRepository, applicationRepository))

    private val paymentService = PaymentService(
        paymentRepository, paymentPersistenceService, tossPaymentClient, settlementService, noticeService
    )

    private fun createPayment(post: Post, status: PaymentStatus): Payment {
        val hourlyPaySnapshot = 15_000
        val hours = BigDecimal.TEN
        val amount = hours.multiply(BigDecimal.valueOf(hourlyPaySnapshot.toLong()))
        // 결제 대기 상태에는 결제 수단·승인 정보가 아직 없다
        val isReady = status == PaymentStatus.READY

        return Payment(
            id = 1L,
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
            .id(1L)
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
        val client = User(
            "client01", "password1!", "client01@email.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
        )
        setId(client, 1L)
        return client
    }

    private fun createEscort(): User {
        val escort = User(
            "escort01", "password1!", "escort01@email.com", "동행매니저",
            Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
        )
        setId(escort, 2L)
        return escort
    }

    // 저장하지 않은 엔티티에도 id 가 필요해서 리플렉션으로 넣는다
    private fun setId(user: User, id: Long) {
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
    }

    @Test
    @DisplayName("[PaymentService] 공고 삭제 시 결제는 취소 상태로 변경")
    fun cancelPostAndPayment() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(payment).whenever(paymentRepository).findByPostId(any())

        val deletedPayment = paymentService.cancelPostAndPayment(post.id!!)

        assertThat(deletedPayment.paymentStatus).isEqualTo(PaymentStatus.DELETED)
    }

    @Test
    @DisplayName("[PaymentService] 결제 상태 DELETED 결제 취소 스케줄러 성공")
    fun paymentStatusDeletedToCancelSuccess() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DELETED)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findDeletedAll()
        doReturn(Optional.of(payment)).whenever(paymentRepository).findById(any())

        val counts = paymentService.cancelPostAndPaymentCallApi()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(1)
        assertThat(counts[2]).isEqualTo(0)
        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    @DisplayName("[PaymentService] 결제 상태 DELETED 결제 취소 스케줄러 실패 - 외부 API 호출")
    fun paymentStatusDeletedToCancelFailedThirdParty() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DELETED)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findDeletedAll()
        doThrow(RuntimeException("토스 페이먼츠 API 호출 실패"))
            .whenever(tossPaymentClient).callApiCancel(any(), any(), any())

        val counts = paymentService.cancelPostAndPaymentCallApi()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(1)
    }

    @Test
    @DisplayName("[PaymentService] 결제 상태 DELETED 결제 취소 스케줄러 실패 - DB 저장")
    fun paymentStatusDeletedToCancelFailedDb() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DELETED)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findDeletedAll()
        doThrow(RuntimeException("DB 저장 실패"))
            .whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())

        val counts = paymentService.cancelPostAndPaymentCallApi()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(1)
    }

    @Test
    @DisplayName("[PaymentService] 결제 재승인 시 실패")
    fun paymentReConfirmFailed() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)
        val request = PaymentConfirmRequest(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), payment.amount.toString()
        )

        doReturn(payment).whenever(paymentPersistenceService).findById(any(), any())

        assertThatThrownBy { paymentService.confirm(request, payment.requiredId, client.id) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 결제를 완료하셨습니다.")
    }

    @Test
    @DisplayName("[PaymentService] CANCELED 결제 재승인 → 거부")
    fun paymentReConfirmCanceledStatus() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.CANCELED)
        val request = PaymentConfirmRequest(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), payment.amount.toString()
        )

        doReturn(payment).whenever(paymentPersistenceService).findById(any(), any())

        assertThatThrownBy { paymentService.confirm(request, payment.requiredId, client.id) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("취소된 결제입니다.")
    }

    @Test
    @DisplayName("[PaymentService] 결제 상태 CANCELED에서 결제 취소 요청")
    fun paymentReCancelFailed() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.CANCELED)

        assertThatThrownBy {
            paymentService.cancel(payment, PaymentCancelRequest(""), BigDecimal.ZERO, false, true)
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 취소된 결제입니다.")
    }

    @Test
    @DisplayName("[PaymentService] READY(미결제) 결제 취소")
    fun cancelStatusReady() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.READY)

        assertThatThrownBy {
            paymentService.cancel(payment, PaymentCancelRequest(""), BigDecimal.ZERO, false, true)
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 완료 상태가 아닙니다.")
    }

    @Test
    @DisplayName("[PaymentService] 동행 시간 단축 시 차액 부분 취소(PARTIAL_CANCELED)")
    fun paymentPartialCanceled() {
        val client = createClient()
        val post = createPost(client, 4)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findByPostId(any())
        doReturn(payment).whenever(paymentPersistenceService).paymentPartialCancelDb(any(), any(), any())

        paymentService.validPayment(
            post,
            Application.builder().escort(createEscort()).build(),
            LocalDate.now()
        )

        // 결제 150,000원 - 실제 동행 4시간(60,000원) = 차액 90,000원만 부분 취소
        verify(paymentPersistenceService, times(1)).paymentPartialCancelDb(
            eq(payment.requiredId),
            any(),
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(90_000L)) == 0 }
        )
        verify(noticeService, times(1)).send(eq(client.id), any(), anyOrNull())
    }

    @Test
    @DisplayName("[PaymentService] 동행 시간 초과 시 추가 결제(READY) 생성")
    fun paymentNewAddPayment() {
        val client = createClient()
        val post = createPost(client, 12)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findByPostId(any())
        doReturn(payment).whenever(paymentPersistenceService).createPayment(any())

        paymentService.validPayment(
            post,
            Application.builder().escort(createEscort()).build(),
            LocalDate.now()
        )

        // 실제 동행 12시간(180,000원) - 기존 결제 150,000원 = 30,000원 추가 결제 생성
        verify(paymentPersistenceService, times(1)).createPayment(
            argThat<Payment> {
                amount.compareTo(BigDecimal.valueOf(30_000L)) == 0 &&
                        paymentStatus == PaymentStatus.READY
            }
        )
        verify(noticeService, times(1)).send(eq(client.id), any(), anyOrNull())
    }

    @Test
    @DisplayName("[PaymentService] 지원 취소 시 모집 기한 종료이면 해당 공고 결제 취소 - 결제 DONE 1개")
    fun cancelPaymentStatusDoneWithApplicationCancel() {
        // given
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findByPostId(any())
        doReturn(payment).whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())

        // when
        paymentService.cancel(post)

        // then
        verify(tossPaymentClient, times(1)).callApiCancel(any(), any(), any())
        verify(paymentPersistenceService, times(1)).paymentCancelDb(any(), any(), any())
    }

    // 승인 성공까지 가기 위한 공통 스텁 (결제 조회 → 상태 선점 → 외부 승인 API)
    private fun stubConfirmFlow(payment: Payment) {
        doReturn(payment).whenever(paymentPersistenceService).findById(any(), any())
        doReturn(1).whenever(paymentRepository).paymentInProgress(any())
        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", payment.amount.toString())))
            .whenever(tossPaymentClient).callApiConfirm(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentService] 결제 승인 성공 시 의뢰인에게 알림 발송")
    fun confirmSendsNotice() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.READY)
        stubConfirmFlow(payment)
        doReturn(payment).whenever(paymentPersistenceService).paymentSaveDb(any(), any(), any(), any())

        paymentService.confirm(
            PaymentConfirmRequest("pk", "order", payment.amount.toString()), payment.requiredId, client.id
        )

        verify(noticeService, times(1)).send(
            eq(client.id),
            argThat<String> { contains(post.title) },
            anyOrNull()
        )
    }

    @Test
    @DisplayName("[PaymentService] 승인 후 DB 반영에서 결제를 못 찾으면 복구를 시도한다")
    fun confirmRecoversWhenPaymentNotFound() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.READY)
        stubConfirmFlow(payment)
        doThrow(NotFoundException(41, "결제 정보를 찾을 수 없습니다."))
            .whenever(paymentPersistenceService).paymentSaveDb(any(), any(), any(), any())

        // 현재 동작: 복구용 cancel 이 IN_PROGRESS 결제를 취소하려다 막혀서
        // 원래 예외(NotFoundException) 대신 아래 예외가 호출자에게 전달된다.
        // PaymentService.confirm 의 복구 경로를 고치면 이 테스트도 함께 바꿔야 한다.
        assertThatThrownBy {
            paymentService.confirm(
                PaymentConfirmRequest("pk", "order", payment.amount.toString()), payment.requiredId, client.id
            )
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 완료 상태가 아닙니다.")

        verify(tossPaymentClient, never()).callApiCancel(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentService] 승인 후 DB 반영에서 서버 에러가 나면 복구를 시도한다")
    fun confirmRecoversWhenDbFails() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.READY)
        stubConfirmFlow(payment)
        doThrow(RuntimeException("DB 저장 실패"))
            .whenever(paymentPersistenceService).paymentSaveDb(any(), any(), any(), any())

        // 현재 동작: InternalServerErrorException(41) 로 감싸기 전에 복구용 cancel 이 먼저 막힌다 (위 테스트와 같은 원인)
        assertThatThrownBy {
            paymentService.confirm(
                PaymentConfirmRequest("pk", "order", payment.amount.toString()), payment.requiredId, client.id
            )
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 완료 상태가 아닙니다.")
    }

    @Test
    @DisplayName("[PaymentService] 토스 결제 키가 없는 결제는 취소할 수 없다")
    fun cancelWithoutPaymentKey() {
        val client = createClient()
        val post = createPost(client)
        val payment = Payment(
            id = 1L,
            amount = BigDecimal.valueOf(150_000),
            hourlyPaySnapshot = 15_000,
            hours = BigDecimal.TEN,
            post = post,
            method = "CARD",
            paymentStatus = PaymentStatus.DONE,
            orderId = "tossOrderId",
            paymentKey = null,
            approvedAt = LocalDateTime.now().minusDays(1),
            balanceAmount = BigDecimal.valueOf(150_000)
        )

        assertThatThrownBy {
            paymentService.cancel(payment, PaymentCancelRequest("사유"), payment.amount, false, false)
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("토스 페이먼트 키가 등록되지 않았습니다.")
    }

    @Test
    @DisplayName("[PaymentService] 취소 금액이 잔액보다 작으면 부분 취소로 처리한다")
    fun cancelLessThanBalanceGoesPartial() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(payment).whenever(paymentPersistenceService).paymentPartialCancelDb(any(), any(), any())

        paymentService.cancel(payment, PaymentCancelRequest("사유"), BigDecimal.valueOf(50_000), false, false)

        verify(paymentPersistenceService, times(1)).paymentPartialCancelDb(
            eq(payment.requiredId), eq("사유"), argThat<BigDecimal> { compareTo(BigDecimal.valueOf(50_000L)) == 0 }
        )
        verify(paymentPersistenceService, never()).paymentCancelDb(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentService] 매칭된 공고의 결제는 취소할 수 없다")
    fun cancelByUserRejectedWhenPostMatched() {
        val client = createClient()
        val post = Post.builder()
            .id(1L)
            .title("정형외과 동행 구합니다")
            .content("내용")
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
            .escortEndAt(LocalDateTime.now().plusDays(7).plusHours(10))
            .client(client)
            .postStatus(PostStatus.MATCHED)
            .build()
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(payment).whenever(paymentPersistenceService).findById(any(), any())

        assertThatThrownBy { paymentService.cancel(client.id, payment.requiredId, PaymentCancelRequest("사유")) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 종료된 동행이거나 매칭되었기 때문에 결제를 취소할 수 없습니다.")

        verify(tossPaymentClient, never()).callApiCancel(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentService] 의뢰인 결제 취소 시 지원자를 모두 거절하고 알림을 보낸다")
    fun cancelByUserRejectsApplicationsAndSendsNotice() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(payment).whenever(paymentPersistenceService).findById(any(), any())
        doReturn(payment).whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())

        val canceledPayment = paymentService.cancel(client.id, payment.requiredId, PaymentCancelRequest("사유"))

        assertThat(canceledPayment).isSameAs(payment)
        verify(tossPaymentClient, times(1)).callApiCancel(eq("사유"), any(), any())
        verify(paymentPersistenceService, times(1)).paymentCancelDb(eq(payment.requiredId), eq("사유"), eq(true))
        verify(paymentPersistenceService, times(1)).rejectApplication(post)
        verify(noticeService, times(1)).send(eq(client.id), argThat<String> { contains(post.title) }, anyOrNull())
    }

    @Test
    @DisplayName("[PaymentService] 공고 기반 취소는 결제 건마다 취소를 호출한다")
    fun cancelPostWithMultiplePayments() {
        val client = createClient()
        val post = createPost(client)
        val firstPayment = createPayment(post, PaymentStatus.DONE)
        val secondPayment = createPayment(post, PaymentStatus.DONE)

        doReturn(listOf(firstPayment, secondPayment)).whenever(paymentPersistenceService).findByPostId(any())
        doReturn(firstPayment).whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())

        paymentService.cancel(post)

        verify(tossPaymentClient, times(2)).callApiCancel(any(), any(), any())
        // 공고 기반 취소는 재결제 건을 만들지 않는다
        verify(paymentPersistenceService, times(2)).paymentCancelDb(any(), any(), eq(false))
    }

    @Test
    @DisplayName("[PaymentService] 결제 완료 건이 없으면 미결제 금액만 공고 금액으로 맞춘다")
    fun validPaymentWithoutPaidPayment() {
        val client = createClient()
        val post = createPost(client)

        doReturn(emptyList<Payment>()).whenever(paymentPersistenceService).findByPostId(any())
        doNothing().whenever(paymentPersistenceService).updateAmountForModifyPost(any(), any())

        val payoutAmount = paymentService.validPayment(post)

        assertThat(payoutAmount).isEqualByComparingTo(post.totalPay)
        verify(paymentPersistenceService, times(1)).updateAmountForModifyPost(eq(post.id!!), eq(post))
        verify(paymentPersistenceService, never()).createPayment(any())
        verify(paymentPersistenceService, never()).paymentPartialCancelDb(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentService] 결제 금액과 동행 금액이 같으면 추가 결제도 취소도 하지 않는다")
    fun validPaymentWithExactAmount() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findByPostId(any())

        val payoutAmount = paymentService.validPayment(post)

        assertThat(payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(150_000))
        verify(paymentPersistenceService, never()).createPayment(any())
        verify(paymentPersistenceService, never()).paymentPartialCancelDb(any(), any(), any())
        verify(noticeService, never()).send(any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("[PaymentService] 동행 완료 시 정산 데이터를 생성한다")
    fun validPaymentCreatesSettlement() {
        val client = createClient()
        val post = createPost(client)
        val payment = createPayment(post, PaymentStatus.DONE)
        val escort = createEscort()
        val application = Application.builder().escort(escort).build()
        val settledDate = LocalDate.now()

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findByPostId(any())

        paymentService.validPayment(post, application, settledDate)

        verify(settlementService, times(1)).createSettlement(
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(150_000L)) == 0 },
            eq(application),
            eq(escort),
            eq(settledDate)
        )
    }

    @Test
    @DisplayName("[PaymentService] 공고 금액으로 미결제 결제를 생성한다")
    fun createPaymentFromPost() {
        val client = createClient()
        val post = createPost(client, 4)
        val payment = createPayment(post, PaymentStatus.READY)

        doReturn(payment).whenever(paymentPersistenceService).createPayment(any())

        paymentService.createPayment(post)

        verify(paymentPersistenceService, times(1)).createPayment(
            argThat<Payment> {
                amount.compareTo(BigDecimal.valueOf(60_000L)) == 0 &&
                        hours.compareTo(BigDecimal.valueOf(4L)) == 0 &&
                        hourlyPaySnapshot == 15_000 &&
                        paymentStatus == PaymentStatus.READY
            }
        )
    }

    @Test
    @DisplayName("[PaymentService] 미결제 공고 삭제 건은 외부 API 없이 DB만 취소한다")
    fun cancelPostAndPaymentCallApiWithoutPaymentKey() {
        val client = createClient()
        val post = createPost(client)
        val payment = Payment(
            id = 1L,
            amount = BigDecimal.valueOf(150_000),
            hourlyPaySnapshot = 15_000,
            hours = BigDecimal.TEN,
            post = post,
            paymentStatus = PaymentStatus.DELETED
        )

        doReturn(listOf(payment)).whenever(paymentPersistenceService).findDeletedAll()
        doReturn(payment).whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())

        val counts = paymentService.cancelPostAndPaymentCallApi()

        verify(tossPaymentClient, never()).callApiCancel(any(), any(), any())
        verify(paymentPersistenceService, times(1))
            .paymentCancelDb(eq(payment.requiredId), eq("미결제 공고 삭제"), eq(false))

        // 이 건은 성공/실패 어디에도 세지 않아서 집계가 0건으로 남는다 (스케줄러 로그가 실제 처리 건수보다 작게 찍힘)
        assertThat(counts[0]).isEqualTo(0)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(0)
    }

    @Test
    @DisplayName("[PaymentService] 미결제 결제가 없으면 결제 완료로 본다")
    fun validNotPaid() {
        doReturn(emptyList<Payment>()).whenever(paymentRepository).findNotPaidByUserId(any())
        assertThat(paymentService.validNotPaid(1L)).isTrue()

        val payment = createPayment(createPost(createClient()), PaymentStatus.READY)
        doReturn(listOf(payment)).whenever(paymentRepository).findNotPaidByUserId(any())
        assertThat(paymentService.validNotPaid(1L)).isFalse()
    }

    @Test
    @DisplayName("[PaymentService] 동행 완료 후 미결제 추가 결제 여부를 조회한다")
    fun hasUnpaidExtraPayment() {
        doReturn(true).whenever(paymentRepository).existsUnpaidExtraByUserId(any())

        assertThat(paymentService.hasUnpaidExtraPayment(1L)).isTrue()
        verify(paymentRepository, times(1)).existsUnpaidExtraByUserId(1L)
    }

    @Test
    @DisplayName("[PaymentService] 공고별 미결제 결제가 없으면 조회 실패")
    fun findByPostIdAndReadyNotFound() {
        doReturn(null).whenever(paymentRepository).findByPostIdAndUserIdAndReady(any(), any())

        assertThatThrownBy { paymentService.findByPostIdAndReady(1L, 1L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("결제 정보를 찾을 수 없습니다.")
    }

    // deprecated, 비즈니스 로직과 다른 테스트
//    @Test
//    @DisplayName("[PaymentService] 지원 취소 시 모집 기한 종료이면 해당 공고 결제 취소 - 결제 DONE 1개, PARTIAL_CANCELED 1개")
//    fun cancelPaymentStatusDoneAndPartialCanceledWithApplicationCancel() {
//        // given
//        val client = createClient()
//        val post = createPost(client)
//        val donePayment = createPayment(post, PaymentStatus.DONE)
//        val partialCanceledPayment = createPayment(post, PaymentStatus.PARTIAL_CANCELED)
//
//        doReturn(listOf(donePayment, partialCanceledPayment))
//            .whenever(paymentPersistenceService).findByPostId(any())
//        // paymentCancelDb 를 스텁하므로 PARTIAL_CANCELED -> CANCELED 전이는 실행되지 않는다.
//        // 전이 규칙상 막혀 있는 조합이라 실제 운영에서는 예외가 난다 (PaymentStatus.canTransitionTo 확인 필요)
//        doReturn(donePayment).whenever(paymentPersistenceService).paymentCancelDb(any(), any(), any())
//
//        // when
//        paymentService.cancel(post)
//
//        // then
//        verify(tossPaymentClient, times(2)).callApiCancel(any(), any(), any())
//        verify(paymentPersistenceService, times(2)).paymentCancelDb(any(), any(), any())
//    }
}
