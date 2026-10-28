package com.back.nbe12142team06.domain.payment.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.notification.entity.NotificationContent
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.PaymentCancelRequest
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentPersistenceService: PaymentPersistenceService,
    private val tossPaymentClient: TossPaymentClient,
    private val settlementService: SettlementService,
    private val noticeService: NoticeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun confirm(request: PaymentConfirmRequest, paymentId: Long, userId: Long): Payment {
        var payment = paymentPersistenceService.confirmUpdate(userId, paymentId, request.amount)

        val tossPaymentKey = request.paymentKey
        val tossOrderId = request.orderId
        val amount = request.amount

        // 외부 API 호출
        val response: ResponseEntity<TossConfirmResponse>
        try {
            response = tossPaymentClient.callApiConfirm(tossPaymentKey, tossOrderId, amount)
        } catch (e: InternalServerErrorException) {
            // 결제 실패 시 결제 데이터 하드 삭제
            paymentPersistenceService.failedConfirm(paymentId)
            throw e
        }

        // 결과 검증
        val body = response.body
            ?: run {
                log.error("토스 결제 승인 API 실패 - 응답 값이 없습니다.")
                rollbackConfirm(
                    paymentId = paymentId,
                    tossPaymentKey = tossPaymentKey,
                    amount = amount
                )
                throw InternalServerErrorException(43, "토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다.")
            }
        if (body.paymentKey != tossPaymentKey || body.orderId != tossOrderId || body.totalAmount != amount) {
            log.error("토스 결제 승인 API 실패 - 사용자 결제 승인 요청 값과 토스 결제 승인 응답 값이 일치하지 않습니다.")
            rollbackConfirm(
                paymentId = paymentId,
                tossPaymentKey = tossPaymentKey,
                amount = amount
            )
            throw InternalServerErrorException(43, "토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다.")
        }

        // DB 반영
        try {
            payment = paymentPersistenceService.paymentSaveDb(body, paymentId, tossPaymentKey, tossOrderId)
        } catch (e: NotFoundException) {
            log.error("결제 승인 실패", e)
            rollbackConfirm(paymentId, tossPaymentKey, amount)
            throw e
        } catch (ex: RuntimeException) {
            log.error("결제 승인 실패", ex)
            rollbackConfirm(paymentId, tossPaymentKey, amount)
            throw InternalServerErrorException(41, "결제 승인 도중 서버 에러가 발생했습니다.")
        }

        log.info("결제 승인 성공 - paymentId: {}, userId: {}", paymentId, userId)

        // 알림
        noticeService.send(
            userId,
            NotificationContent.C_PAYMENT_DONE.of(payment.post.title, payment.amount.toString()),
            NotificationUrl.U_PAYMENT_DONE.of(payment.post.id!!)
        )

        return payment
    }

    private fun rollbackConfirm(paymentId: Long, tossPaymentKey: String, amount: String) {
        runCatching { tossPaymentClient.callApiCancel("결제 승인 중 서버 에러", tossPaymentKey, amount) }
            .onFailure { log.error("승인 롤백 실패 - 수동 확인 필요, paymentId: {}", paymentId, it) }
        paymentPersistenceService.failedConfirm(paymentId)
    }

    fun findAll(userId: Long): List<Payment> {
        return paymentRepository.findAllByUserId(userId)
    }

    fun findById(userId: Long, paymentId: Long): Payment {
        return paymentPersistenceService.findById(userId, paymentId)
    }

    fun cancel(
        payment: Payment,
        request: PaymentCancelRequest,
        cancelAmount: BigDecimal,
        isPartialCancel: Boolean,
        createNewPayment: Boolean
    ): Payment {
        if (payment.paymentStatus == PaymentStatus.CANCELED ||
            payment.paymentStatus == PaymentStatus.PARTIAL_CANCELED
        ) {
            throw InvalidException(44, "이미 취소된 결제입니다.")
        }

        if (payment.paymentStatus != PaymentStatus.DONE) {
            throw InvalidException(45, "결제 완료 상태가 아닙니다.")
        }

        val tossPaymentKey = payment.paymentKey
            ?: throw InvalidException(48, "토스 페이먼트 키가 등록되지 않았습니다.")
        val cancelReason = request.cancelReason

        // 외부 API 요청
        val response = tossPaymentClient.callApiCancel(cancelReason, tossPaymentKey, cancelAmount.toString())
        val body = response.body
        if (body != null) {
            if (payment.paymentKey != body.paymentKey ||
                payment.orderId != body.orderId ||
                payment.balanceAmount.subtract(cancelAmount).compareTo(BigDecimal(body.balanceAmount)) != 0
            ) {
                log.error(
                    "토스 결제 취소 API 데이터 불일치 - 실제 취소 처리된 것으로 예상되며 정확한 정보는 토스 측에서 직접 확인해야 합니다. 취소 요청은 DB에 반영됩니다. 사용자 취소 요청 값 - paymentKey: {}, orderId: {}, cancelAmount: {} - 토스 취소 응답 값 - paymentKey: {}, orderId: {}, totalAmount: {}, balanceAmount: {}",
                    payment.paymentKey,
                    payment.orderId,
                    cancelAmount,
                    body.paymentKey,
                    body.orderId,
                    body.totalAmount,
                    body.balanceAmount
                )
            }
        } else {
            log.error("토스 결제 취소 API 실패 - 응답 값이 없습니다. 결제는 취소 처리는 했지만 실제 취소 여부는 토스 측에서 직접 확인해야 합니다.")
        }

        // DB 반영
        try {
            if (payment.balanceAmount > cancelAmount || isPartialCancel) {
                paymentPersistenceService.paymentPartialCancelDb(payment.requiredId, cancelReason, cancelAmount)
            } else {
                paymentPersistenceService.paymentCancelDb(payment.requiredId, cancelReason, createNewPayment)
            }
        } catch (e: NotFoundException) {
            log.error("결제 취소 실패", e)
            throw e
        } catch (ex: RuntimeException) {
            log.error("결제 취소 실패", ex)
            throw InternalServerErrorException(42, "결제 취소 도중 서버 에러가 발생했습니다.")
        }

        log.info("결제 취소 성공 - paymentId: {}, cancelAmount: {}", payment.id, cancelAmount)

        return payment
    }

    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun cancel(userId: Long, paymentId: Long, request: PaymentCancelRequest): Payment {
        val payment = paymentPersistenceService.findById(userId, paymentId)

        // 매칭되었거나 동행 완료인 경우 결제 취소 불가
        val post = payment.post
        if (!(post.postStatus == PostStatus.OPEN ||
                    post.postStatus == PostStatus.EXPIRED)
        ) {
            throw InvalidException(46, "이미 종료된 동행이거나 매칭되었기 때문에 결제를 취소할 수 없습니다.")
        }

        if (paymentPersistenceService.cancelPreempt(paymentId) <= 0) {
            throw InvalidException(44, "이미 취소된 결제입니다.")
        }

        val amount = payment.amount
        val canceledPayment = this.cancel(payment, request, amount, false, true)

        // 지원한 사용자 모두 거절
        paymentPersistenceService.rejectApplication(post)

        // 알림
        noticeService.send(
            userId,
            NotificationContent.C_PAYMENT_CANCELED.of(post.title, payment.amount.toString()),
            NotificationUrl.U_PAYMENT_CANCELED.of(post.id!!)
        )

        return canceledPayment
    }

    // 공고 기반 결제 취소
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun cancel(post: Post) {
        val payments = paymentPersistenceService.findByPostId(post.id!!)

        for (payment in payments) {
            cancel(
                payment,
                PaymentCancelRequest("노쇼로 인해 동행 취소, 공고 모집 기한이 만료되어 취소되었습니다."),
                payment.balanceAmount,
                false, false
            )
        }
    }

    fun validPayment(post: Post, application: Application, settledDate: LocalDate) {
        val payoutAmount = validPayment(post)
        // 정산 데이터 생성
        settlementService.createSettlement(payoutAmount, application, application.escort, settledDate)
    }

    fun validPayment(post: Post): BigDecimal {
        // 결제 완료를 모두 가져옴
        val payments: List<Payment> = paymentPersistenceService.findByPostId(post.id!!)

        // 결제 완료된 결제가 없는 경우
        if (payments.isEmpty()) {
            paymentPersistenceService.updateAmountForModifyPost(post.id!!, post)
            return post.totalPay
        }

        var balanceAmount = BigDecimal.ZERO
        for (payment in payments) {
            balanceAmount = balanceAmount.add(payment.amount)
        }

        balanceAmount = balanceAmount.subtract(post.totalPay)
        val payoutAmount = post.totalPay

        // 추가 결제 플로우
        if (balanceAmount < BigDecimal.ZERO) {
            // 결제 데이터 생성
            val newPayment = Payment(
                amount = balanceAmount.abs(),
                hourlyPaySnapshot = post.hourlyPay,
                hours = post.escortHours,
                post = post
            )
            paymentPersistenceService.createPayment(newPayment)

            // 알림
            noticeService.send(
                post.client.id,
                NotificationContent.C_PAYMENT_EXTRA_PAY.of(post.title, newPayment.amount.toString()),
                NotificationUrl.U_PAYMENT_EXTRA_PAY.of(post.id!!)
            )
        } else if (balanceAmount > BigDecimal.ZERO) {
            // 취소 로직 결제 데이터 생성 없애기
            cancel(
                payment = payments.first(),
                request = PaymentCancelRequest("결제 금액: ${balanceAmount.add(payoutAmount)}, 이용 금액: $payoutAmount"),
                cancelAmount = balanceAmount,
                isPartialCancel = true,
                createNewPayment = false
            )

            // 알림
            noticeService.send(
                post.client.id,
                NotificationContent.C_PAYMENT_PARTIAL_CANCELED.of(post.title, balanceAmount.toString()),
                NotificationUrl.U_PAYMENT_PARTIAL_CANCELED.of(post.id!!)
            )
        }

        return payoutAmount
    }

    fun createPayment(post: Post): Payment {
        val payment = Payment(
            null,
            amount = post.totalPay,
            hourlyPaySnapshot = post.hourlyPay,
            hours = post.escortHours,
            post = post
        )
        return paymentPersistenceService.createPayment(payment)
    }

    fun findByPostIdAndReady(postId: Long, userId: Long): Payment {
        return paymentRepository.findByPostIdAndUserIdAndReady(postId, userId)
            ?: throw NotFoundException(41, "결제 정보를 찾을 수 없습니다.")
    }

    fun updateAmount(paymentId: Long, amount: String) {
        paymentPersistenceService.updateAmount(paymentId, amount)
    }

    // 결제 - 공고 삭제 상태 변경
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun cancelPostAndPayment(postId: Long): Payment {
        return paymentPersistenceService.updateDeleteStatus(postId)
    }

    fun cancelPostAndPaymentCallApi(): IntArray {
        val payments = paymentPersistenceService.findDeletedAll()
        var succeedCount = 0
        var failedCount = 0
        var cancelCount = 0

        for (payment in payments) {
            payment.paymentKey ?: run {
                paymentPersistenceService.paymentCancelDb(payment.requiredId, "미결제 공고 삭제", false)
                cancelCount++
                continue
            }

            val tossPaymentKey = payment.paymentKey
                ?: throw InvalidException(48, "토스 페이먼트 키가 등록되지 않았습니다.")
            val cancelReason = "공고 삭제로 인한 결제 취소"

            // 외부 API 요청
            try {
                tossPaymentClient.callApiCancel(cancelReason, tossPaymentKey, payment.balanceAmount.toString())
            } catch (e: RuntimeException) {
                failedCount++
                continue
            }

            // DB 반영
            try {
                paymentPersistenceService.paymentCancelDb(payment.requiredId, cancelReason, false)
            } catch (ex: RuntimeException) {
                log.error("결제 취소 - DB 저장 실패", ex)
                failedCount++
                continue
            }

            succeedCount++
            log.info("결제 취소 성공 - paymentId: {}, cancelAmount: {}", payment.id, payment.balanceAmount)
        }

        return intArrayOf(succeedCount + failedCount + cancelCount, succeedCount, failedCount, cancelCount)
    }

    fun validNotPaid(userId: Long): Boolean {
        val payments = paymentPersistenceService.getNotPaid(userId)

        return payments.isEmpty()
    }

    fun getDailyPaymentAnalytics(startDate: LocalDate?, endDate: LocalDate?): List<PaymentDailyAnalyticsDto> {
        val startDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return paymentPersistenceService.getDailyAnalytics(startDateTime, endDateTime)
    }

    fun getPaymentAnalyticsByPeriod(startDate: LocalDate?, endDate: LocalDate?): List<Payment> {
        val startDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return paymentPersistenceService.getAnalyticsByPeriod(startDateTime, endDateTime)
    }

    fun getPaymentsForAdmin(startDate: LocalDate?, endDate: LocalDate?, pageable: Pageable): Page<Payment> {
        val startDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return paymentPersistenceService.getPaymentsForAdmin(startDateTime, endDateTime, pageable)
    }

    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun updateStatusForAdmin(paymentId: Long, status: PaymentStatus) {
        paymentPersistenceService.updateStatusForAdmin(paymentId, status)
    }

    // 동행 완료 후 추가 결제를 하지 않은 공고가 있는 지 확인
    fun hasUnpaidExtraPayment(userId: Long): Boolean {
        return this.paymentRepository.existsUnpaidExtraByUserId(userId)
    }
}
