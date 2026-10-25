package com.back.nbe12142team06.domain.penalty.entity

enum class NoShowPenaltyStatus(
    val description: String
) {
    PENDING("적용전"), APPLIED("완료");

    fun canNoShowPenalty(status: NoShowPenaltyStatus): Boolean = when (this) {
        PENDING -> status == APPLIED
        else -> return false
    }
}
