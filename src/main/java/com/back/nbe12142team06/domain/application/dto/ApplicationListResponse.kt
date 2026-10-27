package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus

data class ApplicationListResponse(
    val applicationId: Long,
    val escortId: Long,
    val escortName: String,
    val status: ApplicationStatus,
) {
    constructor(application: Application) : this(
        application.requiredId,
        application.escort.id,
        application.escort.name,
        application.status,
    )
}
