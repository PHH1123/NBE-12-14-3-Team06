package com.back.nbe12142team06.domain.user.dto.admin

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import java.time.LocalDate
import java.time.LocalDateTime

@JvmRecord
data class AdminUserResponse(
    val id: Long?,
    val username: String,
    val email: String,
    val name: String,
    val role: Role,
    val gender: Gender,
    val birthDate: LocalDate,
    val phoneNum: String,
    val region: String,
    val createdAt: LocalDateTime?,
    val deletedAt: LocalDateTime?,     // 탈퇴하지 않았으면 null
    val userStatus: UserStatus,
    val suspendEndAt: LocalDateTime?,  // 정지 상태일 때만 값이 있음
    val statusReason: String?,         // 정지 상태일 때만 값이 있음
    val deleted: Boolean,
) {
    // userStatusHistory: 현재 정지 이력 (활성 회원이면 null)
    @JvmOverloads
    constructor(user: User, userStatusHistory: UserStatusHistory? = null) : this(
        user.id,
        user.username,
        user.email,
        user.name,
        user.role,
        user.gender,
        user.birthDate,
        user.phoneNum,
        user.region,
        user.createdAt,
        user.deletedAt,
        user.userStatus,
        userStatusHistory?.endAt,
        userStatusHistory?.reason,
        user.isDeleted,
    )
}