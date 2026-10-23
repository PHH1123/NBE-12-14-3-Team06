package com.back.nbe12142team06.domain.post.entity

enum class PostStatus(
    val description: String,
) {

    OPEN("모집 중"),
    MATCHED("매칭 완료"),
    IN_PROGRESS("동행 진행 중"),
    COMPLETED("동행 완료"),
    CANCELED("취소됨"),
    EXPIRED("마감 기한 초과");

    fun canTransitionTo(next: PostStatus): Boolean = when (this) {
        OPEN -> next == MATCHED || next == EXPIRED || next == CANCELED          // 지원 승인 / 모집 마감 / 공고 취소(회원 탈퇴·정지)
        MATCHED -> next == IN_PROGRESS || next == CANCELED || next == OPEN      // 출발 / 매칭 취소 / 동행인 노쇼 후 재오픈
        IN_PROGRESS -> next == COMPLETED                                        // 동행 완료
        COMPLETED, CANCELED, EXPIRED -> false                                   // 종료 상태
    }
}
