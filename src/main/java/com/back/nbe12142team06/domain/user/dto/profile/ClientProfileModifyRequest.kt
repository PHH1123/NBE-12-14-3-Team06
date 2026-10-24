package com.back.nbe12142team06.domain.user.dto.profile

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JvmRecord
data class ClientProfileModifyRequest(
    @field:NotBlank(message = "보호자 이름은 필수 항목입니다.")
    @field:Size(max = 50, message = "보호자 이름은 50자 이하여야 합니다.")
    val emergencyContactName: String?,

    @field:NotBlank(message = "보호자 전화번호는 필수 항목입니다.")
    @field:Size(max = 20, message = "보호자 전화번호는 20자 이하여야 합니다.")
    val emergencyContactPhone: String?,

    @field:Size(max = 500, message = "특이사항은 500자 이하여야 합니다.")
    val careNote: String?,  // 선택 입력 (비어 있으면 서비스에서 기본 문구로 채움)
)