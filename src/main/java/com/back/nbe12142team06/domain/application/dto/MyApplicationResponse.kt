package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.post.entity.PostStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class MyApplicationResponse(
    val applicationId: Long?,
    val postId: Long?,
    val applicationStatus: ApplicationStatus?,
    val postStatus: PostStatus,
    val title: String,
    val hospitalName: String,
    val region: String,
    val hospitalAddress: String,
    val escortStartAt: LocalDateTime,
    val escortEndAt: LocalDateTime,
    val hourlyPay: Int,
    val escortHours: BigDecimal,
    val content: String,
) {
    constructor(application: Application) : this(
        application.id,
        application.post.id,
        application.status,
        application.post.postStatus,
        application.post.title,
        application.post.hospitalName,
        application.post.region,
        application.post.hospitalAddress,
        application.post.escortStartAt,
        application.post.escortEndAt,
        application.post.hourlyPay,
        application.post.escortHours,
        application.post.content,
    )
}
