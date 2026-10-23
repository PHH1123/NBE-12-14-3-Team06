package com.back.nbe12142team06.domain.application.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

@JvmRecord
data class ApplicationLocationRequest(
    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val lat: BigDecimal? = null,

    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val lng: BigDecimal? = null,

    @field:PositiveOrZero
    val accuracy: BigDecimal? = null, // 선택 값 (없어도 됨)
)
