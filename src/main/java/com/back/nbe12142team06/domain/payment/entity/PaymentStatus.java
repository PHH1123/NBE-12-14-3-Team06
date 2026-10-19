package com.back.nbe12142team06.domain.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    READY("결제 대기"),
    IN_PROGRESS("결제 중"),
    DONE("결제 완료"),
    CANCELED("결제 취소"),
    PARTIAL_CANCELED("결제 부분 취소"),
    DELETED("공고 삭제된 결제");

    private final String description;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case READY -> next == IN_PROGRESS || next == DELETED;
            case IN_PROGRESS -> next == DONE;
            case DONE -> next == CANCELED || next == PARTIAL_CANCELED || next == DELETED;
            case CANCELED, PARTIAL_CANCELED -> false;
            case DELETED -> next == CANCELED;
        };
    }
}
