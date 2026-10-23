package com.back.nbe12142team06.domain.payment.entity

enum class PaymentStatus(
    val description: String
) {
    READY("결제 대기"),
    IN_PROGRESS("결제 중"),
    DONE("결제 완료"),
    CANCELED("결제 취소"),
    PARTIAL_CANCELED("결제 부분 취소"),
    DELETED("공고 삭제된 결제");

    fun canTransitionTo(next: PaymentStatus): Boolean {
        return when (this) {
            READY -> next == IN_PROGRESS || next == DELETED
            IN_PROGRESS -> next == DONE
            DONE -> next == CANCELED || next == PARTIAL_CANCELED || next == DELETED
            CANCELED, PARTIAL_CANCELED -> false
            DELETED -> next == CANCELED
        }
    }
}
