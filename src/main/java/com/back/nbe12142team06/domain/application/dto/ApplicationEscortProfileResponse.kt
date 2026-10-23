package com.back.nbe12142team06.domain.application.dto

import com.back.nbe12142team06.domain.user.enums.EscortGrade
import com.back.nbe12142team06.domain.user.enums.Gender

@JvmRecord
data class ApplicationEscortProfileResponse(
    val escortId: Long?,
    val name: String?,
    val verified: Boolean?,
    val intro: String?,
    val completedCount: Int?,
    val rating: Double?,
    val ratingCount: Int?,
    val noShowCount: Int?,
    val grade: EscortGrade?,
    val age: Int?,
    val gender: Gender?,
    val region: String?,
)
