package com.back.nbe12142team06.domain.settlement.entity

import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus.COMPLETED
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus.FAILED
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus.PENDING
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus.PROCESSING
import com.back.nbe12142team06.support.TransitionTestBase

/**
 * SettlementStatus 상태 전이 규칙 테스트.
 * 규칙을 바꾸면 SettlementStatus.canTransitionTo 와 이 표를 함께 고친다.
 */
class SettlementStatusTransitionTest : TransitionTestBase<SettlementStatus>() {

    override fun stateType(): Class<SettlementStatus> = SettlementStatus::class.java

    override fun allowed(): Map<SettlementStatus, Set<SettlementStatus>> = mapOf(
        PENDING to setOf(PROCESSING),
        PROCESSING to setOf(COMPLETED, FAILED),
        FAILED to setOf(PROCESSING),
        COMPLETED to emptySet(),
    )

    override fun canTransition(from: SettlementStatus, to: SettlementStatus): Boolean = from.canTransitionTo(to)
}
