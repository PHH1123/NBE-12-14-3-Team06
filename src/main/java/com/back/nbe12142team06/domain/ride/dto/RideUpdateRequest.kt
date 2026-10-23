package com.back.nbe12142team06.domain.ride.dto

import com.back.nbe12142team06.domain.ride.entity.RideSelect
import jakarta.validation.constraints.NotNull

@JvmRecord
data class RideUpdateRequest(
    @field:NotNull(message = "집 -> 병원 이동 수단을 선택해주세요.")
    val rideSelectToHospital: RideSelect?,
    @field:NotNull(message = "병원 -> 집 이동 수단을 선택해주세요.")
    val rideSelectToHome: RideSelect?
)
