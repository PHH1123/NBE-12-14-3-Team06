package com.back.nbe12142team06.domain.penalty.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
class NoShowPenalty(
    id: Long? = null,
    amount: BigDecimal? = null,
    baseAmount: BigDecimal? = null,
    status: NoShowPenaltyStatus = NoShowPenaltyStatus.PENDING,
    application: Application,
    settlement: Settlement? = null
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    // 패널티 금액
    @Column(precision = 15, scale = 0)
    var amount: BigDecimal? = amount
        protected set

    // 정산 금액
    @Column(precision = 15, scale = 0)
    var baseAmount: BigDecimal? = baseAmount
        protected set

    // 패널티 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NoShowPenaltyStatus = status
        protected set

    // 노쇼 발생한 지원, 패널티 적용 된 원인
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    var application: Application = application
        protected set

    // 패널티 적용된 정산
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    var settlement: Settlement? = settlement
        protected set

    // 노쇼 정보 수정
    fun updateNoShow(amount: BigDecimal, baseAmount: BigDecimal, settlement: Settlement) {
        this.amount = amount
        this.baseAmount = baseAmount
        this.status = NoShowPenaltyStatus.APPLIED
        this.settlement = settlement
    }
}
