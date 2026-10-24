package com.back.nbe12142team06.domain.user.dto.admin

import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.UserStatus
import java.time.LocalDateTime

@JvmRecord
data class AdminUserSuspendResponse(
    val username: String,
    val startAt: LocalDateTime?,  // 정지 이력 생성 시각 (@CreatedDate)
    val endAt: LocalDateTime?,    // 정지가 풀리는 시각 (일시 정지는 생성 시 +7일로 채워짐)
    val userStatus: UserStatus,
) {
    constructor(userStatusHistory: UserStatusHistory) : this(
        userStatusHistory.user.username,
        userStatusHistory.createdAt,
        userStatusHistory.endAt,
        userStatusHistory.status,
    )
}