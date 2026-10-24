package com.back.nbe12142team06.domain.user.dto.login

import com.back.nbe12142team06.domain.user.entity.User

@JvmRecord
data class UserLoginResponse(
    val id: Long?,
    val name: String,
) {
    constructor(user: User) : this(
        user.id,
        user.name,
    )
}