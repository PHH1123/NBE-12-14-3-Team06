package com.back.nbe12142team06.domain.penalty.entity

enum class NoShowPenaltyStatus(
    val description: String
) {
    PENDING("적용전"),
    APPLIED("완료");
}
