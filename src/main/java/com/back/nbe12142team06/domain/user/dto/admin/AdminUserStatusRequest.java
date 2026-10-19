package com.back.nbe12142team06.domain.user.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminUserStatusRequest(
        @NotBlank(message = "정지 사유는 필수 항목입니다.")
        String reason
) {
}
