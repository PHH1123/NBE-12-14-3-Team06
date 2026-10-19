package com.back.nbe12142team06.domain.user.dto.admin;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String name,
        Role role,
        Gender gender,
        LocalDate birthDate,
        String phoneNum,
        String region,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        UserStatus userStatus,
        LocalDateTime suspendEndAt,
        String statusReason,
        boolean deleted
) {

    public AdminUserResponse(User user) {
        this(user, null);
    }


    public AdminUserResponse(User user, UserStatusHistory userStatusHistory) {
        this(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getGender(),
                user.getBirthDate(),
                user.getPhoneNum(),
                user.getRegion(),
                user.getCreatedAt(),
                user.getDeletedAt(),
                user.getUserStatus(),
                userStatusHistory == null ? null : userStatusHistory.getEndAt(),
                userStatusHistory == null ? null : userStatusHistory.getReason(),
                user.isDeleted()
        );
    }
}
