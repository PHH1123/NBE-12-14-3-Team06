package com.back.nbe12142team06.domain.user.dto.admin;

import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;

import java.time.LocalDateTime;

public record AdminUserSuspendResponse(
        String username,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public AdminUserSuspendResponse(UserStatusHistory userStatusHistory){
        this(
                userStatusHistory.getUser().getUsername(),
                userStatusHistory.getCreatedAt(),
                userStatusHistory.getEndAt()
        );
    }
}
