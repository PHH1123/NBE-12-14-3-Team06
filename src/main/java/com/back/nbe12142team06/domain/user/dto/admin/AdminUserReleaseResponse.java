package com.back.nbe12142team06.domain.user.dto.admin;

import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import com.back.nbe12142team06.domain.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserReleaseResponse(
        String username,
        LocalDateTime startAt,
        UserStatus userStatus
) {
    public AdminUserReleaseResponse(UserStatusHistory userStatusHistory){
        this(
                userStatusHistory.getUser().getUsername(),
                userStatusHistory.getCreatedAt(),
                userStatusHistory.getStatus()
        );
    }
}
