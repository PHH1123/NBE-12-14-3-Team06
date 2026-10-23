package com.back.nbe12142team06.domain.penalty.entity;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoShowPenalty extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 패널티 금액
    @Column(precision = 15, scale = 0)
    private BigDecimal amount;

    // 정산 금액
    @Column(precision = 15, scale = 0)
    private BigDecimal baseAmount;

    // 패널티 상태
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NoShowPenaltyStatus status = NoShowPenaltyStatus.PENDING;

    // 노쇼 발생한 지원, 패널티 적용 된 원인
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    // 패널티 적용된 정산
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;

    // 노쇼 정보 수정
    public void updateNoShow(BigDecimal amount, BigDecimal baseAmount, Settlement settlement) {
        this.amount = amount;
        this.baseAmount = baseAmount;
        this.status = NoShowPenaltyStatus.APPLIED;
        this.settlement = settlement;
    }
}
