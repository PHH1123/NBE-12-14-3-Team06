package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.application.entity.Application
import java.math.BigDecimal
import java.time.Instant

@JvmRecord
data class ApplicationLocationResponse(
    val lat: BigDecimal?,
    val lng: BigDecimal?,
    val accuracy: BigDecimal?,
    val updatedAt: Instant?,
) {
    constructor(application: Application) : this(
        application.lat,
        application.lng,
        application.accuracy,
        application.locationUpdatedAt,
    )
}
