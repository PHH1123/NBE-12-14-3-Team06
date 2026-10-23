package com.back.nbe12142team06.domain.application.dto

@JvmRecord
data class ApplicationClientProfileResponse(
    val clientId: Long?,
    val clientName: String?,
    val clientPhone: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val careNote: String?,
)
