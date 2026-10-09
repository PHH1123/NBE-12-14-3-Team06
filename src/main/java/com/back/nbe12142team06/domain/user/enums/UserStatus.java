package com.back.nbe12142team06.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성"),
    SUSPENDED("정지"),
    BANNED("영구정지");

    private final String description;
}
