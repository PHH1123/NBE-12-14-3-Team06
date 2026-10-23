package com.back.nbe12142team06.domain.post.dto

import com.back.nbe12142team06.domain.ride.entity.RideSelect
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

// 공고 등록용
// 필수 항목도 타입은 null 을 허용한다. 값이 빠진 요청이 JSON 변환에서 막히지 않고 검증 메시지(@NotBlank 등)로 응답되게 하기 위해서다.
// 숫자·불리언은 값이 없으면 0 / false 가 되고 @Min 검증에서 걸러진다 (Java record 와 같은 동작).
@JvmRecord
data class PostWriteRequest(

    @field:NotBlank(message = "제목은 필수 항목입니다.")
    val title: String?,
    @field:NotBlank(message = "내용은 필수 항목입니다.")
    val content: String?,
    @field:NotBlank(message = "지역은 필수 항목입니다.")
    val region: String?,
    @field:NotBlank(message = "병원명은 필수 항목입니다.")
    val hospitalName: String?,
    @field:NotBlank(message = "병원 주소는 필수 항목입니다.")
    val hospitalAddress: String?,
    @field:NotNull(message = "병원 위도는 필수 항목입니다.")
    val hospitalLat: BigDecimal?,
    @field:NotNull(message = "병원 경도는 필수 항목입니다.")
    val hospitalLng: BigDecimal?,
    @field:NotBlank(message = "픽업 주소는 필수 항목입니다.")
    val pickupAddress: String?,
    @field:NotNull(message = "픽업 위도는 필수 항목입니다.")
    val pickupLat: BigDecimal?,
    @field:NotNull(message = "픽업 경도는 필수 항목입니다.")
    val pickupLng: BigDecimal?,
    @field:Min(value = 11352, message = "시급은 최저시급(10,320원) 기준 수수료 10%를 더한 11,352원 이상이어야 합니다.")
    val hourlyPay: Int = 0,
    @field:NotNull(message = "모집 시작 시간은 필수 항목입니다.")
    val recruitStartAt: LocalDateTime?,
    @field:NotNull(message = "모집 마감 시간은 필수 항목입니다.")
    val recruitEndAt: LocalDateTime?,
    @field:NotNull(message = "동행 시작 시간은 필수 항목입니다.")
    val escortStartAt: LocalDateTime?,
    @field:NotNull(message = "동행 종료 시간은 필수 항목입니다.")
    val escortEndAt: LocalDateTime?,
    @field:NotNull(message = "집 -> 병원 이동 수단은 필수 항목입니다.")
    val rideSelectToHospital: RideSelect?,
    @field:NotNull(message = "병원 -> 집 이동 수단은 필수 항목입니다.")
    val rideSelectToHome: RideSelect?,
    @field:Size(max = 500, message = "환자 특이사항은 500자 이하여야 합니다.")
    val patientNote: String?,       // nullable - 선택 항목
    val reportRequired: Boolean = false,    // 값이 없으면 false (Java record 의 boolean 기본값과 같다)
)
