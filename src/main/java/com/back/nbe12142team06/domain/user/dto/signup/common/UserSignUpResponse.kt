package com.back.nbe12142team06.domain.user.dto.signup.common

import com.back.nbe12142team06.domain.user.entity.User

data class UserSignUpResponse(
    val id: Long?,
    val name: String,
) {
    constructor(user: User) : this(
        user.id,
        user.name,
    )
}