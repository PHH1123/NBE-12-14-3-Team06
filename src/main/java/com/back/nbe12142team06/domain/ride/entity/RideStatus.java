package com.back.nbe12142team06.domain.ride.entity;

import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RideStatus {
    ACCEPTED("이동수단 선택 완료"),
    IN_PROGRESS("이동 중"),
    COMPLETED("이동 완료");

    private final String description;

    public boolean canTransitionTo(RideStatus next) {
        return switch (this) {
            case ACCEPTED -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED -> false;
        };
    }
}
