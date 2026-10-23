package com.back.nbe12142team06.domain.payment.scheduler

import com.back.nbe12142team06.domain.payment.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentScheduler(
    private val paymentService: PaymentService
) {

    private val log = LoggerFactory.getLogger(PaymentScheduler::class.java)

    @Scheduled(cron = "0 30 * * * *")
    fun cancelPaymentScheduler() {
        val counts = paymentService.cancelPostAndPaymentCallApi()

        if (counts[0] > 0) {
            log.info("공고 삭제 - 결제 취소 스케줄링 총 {}건, 성공 {}건, 실패 {}건", counts[0], counts[1], counts[2])
        }
    }
}
