package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus

data class ApplicationAcceptResponse(
    val applicationId: Long,
    val postId: Long,
    val escortId: Long,
    val status: ApplicationStatus,
) {
    constructor(application: Application) : this(
        application.requiredId,
        application.post.requiredId,
        application.escort.id,
        application.status,
    )
}
