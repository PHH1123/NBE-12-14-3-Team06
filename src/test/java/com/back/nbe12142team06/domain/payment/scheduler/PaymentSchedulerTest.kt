package com.back.nbe12142team06.domain.payment.scheduler

import com.back.nbe12142team06.domain.payment.service.PaymentService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 스케줄러는 결제 취소 호출을 위임하고 집계를 로그로만 남긴다.
 */
class PaymentSchedulerTest {

    private val paymentService: PaymentService = mock()
    private val paymentScheduler = PaymentScheduler(paymentService)

    @Test
    @DisplayName("[PaymentScheduler] 공고 삭제 결제 취소를 호출한다")
    fun cancelPaymentScheduler() {
        doReturn(intArrayOf(2, 1, 1)).whenever(paymentService).cancelPostAndPaymentCallApi()

        paymentScheduler.cancelPaymentScheduler()

        verify(paymentService, times(1)).cancelPostAndPaymentCallApi()
    }

    @Test
    @DisplayName("[PaymentScheduler] 취소할 결제가 없어도 예외 없이 끝난다")
    fun cancelPaymentSchedulerWithoutTarget() {
        doReturn(intArrayOf(0, 0, 0)).whenever(paymentService).cancelPostAndPaymentCallApi()

        paymentScheduler.cancelPaymentScheduler()

        verify(paymentService, times(1)).cancelPostAndPaymentCallApi()
    }
}
