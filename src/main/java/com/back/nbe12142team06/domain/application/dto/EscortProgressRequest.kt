package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.application.enums.EscortProgress
import jakarta.validation.constraints.NotNull

@JvmRecord
data class EscortProgressRequest(
    @field:NotNull
    val progress: EscortProgress? = null,
)
