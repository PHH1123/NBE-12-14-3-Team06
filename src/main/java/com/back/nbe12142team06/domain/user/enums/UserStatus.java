package com.back.nbe12142team06.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성"),
    SUSPENDED("기간 정지"),
    BANNED("영구 정지");

    private final String description;

    // 상태 전이 규칙
    public boolean canTransitionTo(UserStatus next) {
       return switch (this) {
           case ACTIVE -> next == SUSPENDED || next == BANNED;
           case SUSPENDED -> next == ACTIVE || next == BANNED;
           case BANNED -> next == ACTIVE;
       };
    }
}
