package com.back.nbe12142team06.domain.user.dto.profile

import com.back.nbe12142team06.domain.user.entity.EscortProfile

data class EscortProfileModifyResponse(
    val userId: Long?,
    val name: String,
    val region: String,
    val intro: String,
    val averageRating: Double?,  // 받은 평점이 없으면 null
    val completedCount: Int,
    val verified: Boolean,
    val bankName: String,
    val accountHolder: String,
    val accountNumber: String,
) {
    constructor(escortProfile: EscortProfile) : this(
        escortProfile.userId,
        escortProfile.user.name,
        escortProfile.user.region,
        escortProfile.intro,
        escortProfile.averageRating,
        escortProfile.completedCount,
        escortProfile.verified,
        escortProfile.bankName,
        escortProfile.accountHolder,
        escortProfile.accountNumber,
    )
}