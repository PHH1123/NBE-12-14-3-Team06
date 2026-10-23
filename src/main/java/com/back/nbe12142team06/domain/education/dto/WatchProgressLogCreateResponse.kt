package com.back.nbe12142team06.domain.education.dto

import com.back.nbe12142team06.domain.education.entity.EducationProgress

@JvmRecord
data class WatchProgressLogCreateResponse(
    val maxWatchedSec: Double,  // 서버가 인정한 재생 위치
    val completed: Boolean,     // 해당 영상 시청 완료 여부
    val verified: Boolean,      // 교육 이수(인증) 여부
) {
    constructor(progress: EducationProgress) : this(
        progress.maxWatchedSec,
        progress.completed,
        progress.escortProfile.verified,
    )
}