package com.back.nbe12142team06.domain.application.enums

enum class ApplicationStatus(val description: String) {
    PENDING("지원 대기"),
    ACCEPTED("지원 승인"),
    REJECTED("지원 거절"),
    CANCELED("지원 취소"),
    NO_SHOW("노쇼");

    // 지원 상태 전환 가능 여부 확인
    fun canTransitionTo(next: ApplicationStatus?): Boolean {
        if (next == null) return false

        return when (this) {
            PENDING -> next == ACCEPTED || next == REJECTED || next == CANCELED
            ACCEPTED -> next == CANCELED || next == NO_SHOW
            REJECTED, CANCELED, NO_SHOW -> false
        }
    }
}
