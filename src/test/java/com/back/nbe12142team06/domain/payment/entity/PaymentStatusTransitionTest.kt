package com.back.nbe12142team06.domain.payment.entity

import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.CANCELED
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.DELETED
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.DONE
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.IN_PROGRESS
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.PARTIAL_CANCELED
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus.READY
import com.back.nbe12142team06.support.TransitionTestBase

/**
 * PaymentStatus 상태 전이 규칙 테스트.
 * 규칙을 바꾸면 PaymentStatus.canTransitionTo 와 이 표를 함께 고친다.
 */
class PaymentStatusTransitionTest : TransitionTestBase<PaymentStatus>() {

    override fun stateType(): Class<PaymentStatus> = PaymentStatus::class.java

    override fun allowed(): Map<PaymentStatus, Set<PaymentStatus>> = mapOf(
        READY to setOf(IN_PROGRESS, DELETED),
        IN_PROGRESS to setOf(DONE),
        DONE to setOf(CANCELED, PARTIAL_CANCELED, DELETED),
        CANCELED to emptySet(),
        PARTIAL_CANCELED to emptySet(),
        DELETED to setOf(CANCELED),
    )

    override fun canTransition(from: PaymentStatus, to: PaymentStatus): Boolean = from.canTransitionTo(to)
}
