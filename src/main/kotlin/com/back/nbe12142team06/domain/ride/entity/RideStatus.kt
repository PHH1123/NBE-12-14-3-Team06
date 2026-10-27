package com.back.nbe12142team06.domain.ride.entity

enum class RideStatus(
    val description: String
) {
    ACCEPTED("이동수단 선택 완료"),
    IN_PROGRESS("이동 중"),
    COMPLETED("이동 완료");

    fun canTransitionTo(next: RideStatus): Boolean {
        return when (this) {
            ACCEPTED -> next == IN_PROGRESS
            IN_PROGRESS -> next == COMPLETED
            COMPLETED -> false
        }
    }
}
