package com.back.nbe12142team06.domain.settlement.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity
import com.back.nbe12142team06.global.exception.InvalidException
import jakarta.persistence.*
import lombok.*
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 정산 엔티티
 * 플랫폼 -> 동행 매니저 정산
 */
@Entity
@Table(indexes = [Index(name = "idx_settlement_settledDate", columnList = "settledDate")])
class Settlement(
    id: Long? = null,
    payoutAmount: BigDecimal,
    platformFee: BigDecimal = BigDecimal.ZERO,
    penaltyAmount: BigDecimal = BigDecimal.ZERO,
    settlementStatus: SettlementStatus = SettlementStatus.PENDING,
    settledDate: LocalDate? = null,
    application: Application,
    escort: User
) : BaseSoftDeleteTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    // 실 지급액, 최소 0원 이상이어야 함
    @Column(
        nullable = false,
        check = [CheckConstraint(name = "chk_payout_amount", constraint = "payout_amount >= 0")],
        precision = 15,
        scale = 0
    )
    var payoutAmount: BigDecimal = payoutAmount
        protected set

    // 플랫폼 수수료, 최소 0원 이상이어야 함
    @Column(
        nullable = false,
        check = [CheckConstraint(name = "chk_platform_fee", constraint = "platform_fee >= 0")],
        precision = 15,
        scale = 0
    )
    var platformFee: BigDecimal = platformFee
        protected set

    // 패널티 금액, 최소 0원 이상이어야 함
    @Column(
        nullable = false,
        check = [CheckConstraint(name = "chk_penalty_amount", constraint = "penalty_amount >= 0")],
        precision = 15,
        scale = 0
    )
    var penaltyAmount: BigDecimal = penaltyAmount
        protected set

    // 정산 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var settlementStatus = settlementStatus
        protected set

    // 정산 일자
    var settledDate: LocalDate? = settledDate
        protected set

    // 의뢰 지원
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true)
    var application: Application = application
        protected set

    // 동행 매니저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escort_id")
    var escort: User = escort
        protected set

    val requiredId : Long
        get() = id ?: error("ID가 NULL입니다.")

    private fun updateStatus(status: SettlementStatus) {
        if (!this.settlementStatus.canTransitionTo(status)) {
            throw InvalidException(
                72, "'${this.settlementStatus.description}' 상태의 정산은 '${status.description}' 상태로 바꿀 수 없습니다."
            )
        }
        this.settlementStatus = status
    }

    fun settlementCompleted() {
        updateStatus(SettlementStatus.COMPLETED)
    }

    fun settlementFailed() {
        updateStatus(SettlementStatus.FAILED)
    }

    fun settlementDateUpdate(updateDate: LocalDate) {
        this.settledDate = updateDate
    }
}
