package com.back.nbe12142team06.domain.user.dto.admin

import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.UserStatus
import java.time.LocalDateTime

@JvmRecord
data class AdminUserBanResponse(
    val username: String,
    val startAt: LocalDateTime?,  // 정지 이력 생성 시각 (@CreatedDate)
    val userStatus: UserStatus,
) {
    constructor(userStatusHistory: UserStatusHistory) : this(
        userStatusHistory.user.username,
        userStatusHistory.createdAt,
        userStatusHistory.status,
    )
}