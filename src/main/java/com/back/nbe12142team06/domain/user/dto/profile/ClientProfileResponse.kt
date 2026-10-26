package com.back.nbe12142team06.domain.user.dto.profile

import com.back.nbe12142team06.domain.user.entity.ClientProfile

data class ClientProfileResponse(
    val userId: Long?,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val careNote: String?,
) {
    constructor(profile: ClientProfile) : this(
        profile.userId,
        profile.emergencyContactName,
        profile.emergencyContactPhone,
        profile.careNote,
    )
}