package com.back.nbe12142team06.domain.user.dto.user

import jakarta.validation.constraints.*
import java.time.LocalDate

class UserProfileUpdateRequest(
    @param:NotBlank(message = "비밀번호는 필수 항목입니다.") val password: @NotBlank(message = "비밀번호는 필수 항목입니다.") String?,
    @NotBlank(message = "이메일은 필수 항목입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") // 이메일 형식 검증
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") email: String?,
    @param:NotBlank(
        message = "이름은 필수 항목입니다."
    ) @param:Size(
        max = 50,
        message = "이름은 50자 이하여야 합니다."
    ) val name: @Size(
        max = 50,
        message = "이름은 50자 이하여야 합니다."
    ) @NotBlank(message = "이름은 필수 항목입니다.") String?,
    @param:NotNull(message = "생년월일은 필수 항목입니다.") @param:Past(
        message = "생년월일은 과거 날짜여야 합니다."
    ) val birthDate: @Past(message = "생년월일은 과거 날짜여야 합니다.") @NotNull(message = "생년월일은 필수 항목입니다.") LocalDate?,
    @param:NotBlank(
        message = "전화번호는 필수 항목입니다."
    ) @param:Pattern(
        regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
        message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)"
    ) val phoneNum: @Pattern(
        regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
        message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)"
    ) @NotBlank(message = "전화번호는 필수 항목입니다.") String?,
    @param:NotBlank(message = "지역은 필수 항목입니다.") @param:Size(
        max = 50
    ) val region: @Size(max = 50) @NotBlank(message = "지역은 필수 항목입니다.") String?
) {
    val email: @NotBlank(message = "이메일은 필수 항목입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(
        max = 100,
        message = "이메일은 100자 이하여야 합니다."
    ) String?

    init {
        var email = email
        if (email != null) {
            email = email.trim().lowercase()
        }
        this.email = email
    }
}