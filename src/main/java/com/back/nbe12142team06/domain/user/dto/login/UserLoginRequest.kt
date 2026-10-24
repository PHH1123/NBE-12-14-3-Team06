package com.back.nbe12142team06.domain.user.dto.login

import jakarta.validation.constraints.NotBlank

@JvmRecord
data class UserLoginRequest(
    @field:NotBlank(message = "아이디는 필수 항목입니다.")
    val username: String?,

    @field:NotBlank(message = "비밀번호는 필수 항목입니다.")
    val password: String?,
)