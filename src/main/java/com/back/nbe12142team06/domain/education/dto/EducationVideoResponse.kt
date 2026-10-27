package com.back.nbe12142team06.domain.education.dto

import com.back.nbe12142team06.domain.education.entity.EducationProgress

data class EducationVideoResponse(
    val videoId: Long,
    val title: String,
    val url: String,
    val durationSec: Int,
    val required: Boolean,
    val maxWatchedSec: Double,  // 서버가 인정한 재생 위치 (이어보기 위치)
    val completed: Boolean,     // 해당 영상 시청 완료 여부
) {
    constructor(progress: EducationProgress) : this(
        progress.educationVideo.requiredId,
        progress.educationVideo.title,
        progress.educationVideo.url,
        progress.educationVideo.durationSec,
        progress.educationVideo.required,
        progress.maxWatchedSec,
        progress.completed,
    )
}