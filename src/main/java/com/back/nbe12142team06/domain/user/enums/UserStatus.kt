package com.back.nbe12142team06.domain.user.enums

enum class UserStatus(
    val description: String
) {
    ACTIVE("활성"),
    SUSPENDED("기간 정지"),
    BANNED("영구 정지");

    // 상태 전이 규칙
    fun canTransitionTo(next: UserStatus): Boolean {
        return when (this) {
            ACTIVE -> next == SUSPENDED || next == BANNED
            SUSPENDED -> next == ACTIVE || next == BANNED
            BANNED -> next == ACTIVE
        }
    }
}
