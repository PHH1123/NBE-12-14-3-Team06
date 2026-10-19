package com.back.nbe12142team06.domain.post.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostStatus {

    OPEN("모집 중"),
    MATCHED("매칭 완료"),
    IN_PROGRESS("동행 진행 중"),
    COMPLETED("동행 완료"),
    CANCELED("취소됨"),
    EXPIRED("마감 기한 초과");

    private final String description;

    public boolean canTransitionTo(PostStatus next) {
        return switch (this) {
            case OPEN -> next == MATCHED || next == EXPIRED || next == CANCELED;          // 지원 승인 / 모집 마감 / 공고 취소(회원 탈퇴·정지)
            case MATCHED -> next == IN_PROGRESS || next == CANCELED || next == OPEN;      // 출발 / 매칭 취소 / 동행인 노쇼 후 재오픈
            case IN_PROGRESS -> next == COMPLETED;                                        // 동행 완료
            case COMPLETED, CANCELED, EXPIRED -> false;                                   // 종료 상태
        };
    }
}