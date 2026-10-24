package com.back.nbe12142team06.domain.user.dto.user

import jakarta.validation.constraints.*
import java.time.LocalDate

// email 을 정규화해서 보관해야 해서 data class / @JvmRecord 대신 일반 클래스로 둔다
class UserProfileUpdateRequest(
    @field:NotBlank(message = "비밀번호는 필수 항목입니다.")
    val password: String?,

    email: String?,  // 아래 email 프로퍼티에서 정규화해서 보관

    @field:NotBlank(message = "이름은 필수 항목입니다.")
    @field:Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    val name: String?,

    @field:NotNull(message = "생년월일은 필수 항목입니다.")
    @field:Past(message = "생년월일은 과거 날짜여야 합니다.")
    val birthDate: LocalDate?,

    @field:NotBlank(message = "전화번호는 필수 항목입니다.")
    @field:Pattern(
        regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
        message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)",
    )
    val phoneNum: String?,

    @field:NotBlank(message = "지역은 필수 항목입니다.")
    @field:Size(max = 50)
    val region: String?,
) {
    // 앞뒤 공백 제거 + 소문자 (Java strip().toLowerCase(Locale.ROOT) 와 같은 기준)
    @field:NotBlank(message = "이메일은 필수 항목입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")  // 이메일 형식 검증
    @field:Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    val email: String? = email?.trim { Character.isWhitespace(it) }?.lowercase()
}