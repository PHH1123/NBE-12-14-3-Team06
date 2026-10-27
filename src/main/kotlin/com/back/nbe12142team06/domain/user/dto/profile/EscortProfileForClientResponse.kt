package com.back.nbe12142team06.domain.user.dto.profile

import com.back.nbe12142team06.domain.user.entity.EscortProfile

// 의뢰인이 보는 동행 매니저 프로필 (계좌 정보 등 민감 정보 제외)
data class EscortProfileForClientResponse(
    val userId: Long,
    val name: String,
    val region: String,
    val intro: String,
    val averageRating: Double?,  // 받은 평점이 없으면 null
    val completedCount: Int,
    val verified: Boolean,
) {
    constructor(escortProfile: EscortProfile) : this(
        escortProfile.requiredId,
        escortProfile.user.name,
        escortProfile.user.region,
        escortProfile.intro,
        escortProfile.averageRating,
        escortProfile.completedCount,
        escortProfile.verified,
    )
}