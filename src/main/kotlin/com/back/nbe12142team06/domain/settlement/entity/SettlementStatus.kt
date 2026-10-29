package com.back.nbe12142team06.domain.settlement.entity

enum class SettlementStatus(
    val description: String
) {
    PENDING("정산 전"),
    PROCESSING("정산 중"),
    COMPLETED("정산 완료"),
    FAILED("정산 실패");

    fun canTransitionTo(next: SettlementStatus): Boolean {
        return when (this) {
            PENDING, FAILED -> next == PROCESSING
            PROCESSING -> next == COMPLETED || next == FAILED
            COMPLETED -> false
        }
    }
}
