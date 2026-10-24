package com.back.nbe12142team06.domain.user.dto.admin

import jakarta.validation.constraints.NotBlank

// 회원 정지(일시/영구), 정지 해제 요청 공용
@JvmRecord
data class AdminUserStatusRequest(
    @field:NotBlank(message = "정지 사유는 필수 항목입니다.")
    val reason: String?,
)