package com.back.nbe12142team06.domain.report.dto

import com.back.nbe12142team06.domain.report.entity.Department
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// 진료 보고서 작성용
// 제목은 화면에서 받지 않고 서비스에서 자동 생성한다.
// 필수 항목도 타입은 null 을 허용한다. 값이 빠진 요청이 JSON 변환에서 막히지 않고 검증 메시지(@NotNull 등)로 응답되게 하기 위해서다.
data class ReportWriteRequest(

    @field:NotNull(message = "진료 과목은 필수 항목입니다.")
    val department: Department?,

    @field:NotBlank(message = "진료 목적은 필수 항목입니다.")
    @field:Size(max = 200, message = "진료 목적은 200자 이하여야 합니다.")
    val purpose: String?,

    @field:NotBlank(message = "진료 내용은 필수 항목입니다.")
    val originContent: String?,

    @field:Size(max = 2000, message = "특이사항은 2000자 이하여야 합니다.")
    val notes: String?,  // 선택 입력
)
