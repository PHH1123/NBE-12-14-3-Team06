package com.back.nbe12142team06.domain.review.dto

import com.back.nbe12142team06.domain.review.entity.Review
import com.back.nbe12142team06.domain.review.entity.ReviewTag
import java.time.LocalDateTime

data class ReviewWriteResponse(
    val id: Long,
    val applicationId: Long,
    val rating: Int,
    val tags: Set<ReviewTag>,
    val content: String?,
    val createdAt: LocalDateTime?,
) {
    constructor(review: Review) : this(
        review.requiredId,
        review.application.requiredId,
        review.rating,
        review.tags,
        review.content,
        review.createdAt,
    )
}
