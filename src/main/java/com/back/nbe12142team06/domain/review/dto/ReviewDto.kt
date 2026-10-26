package com.back.nbe12142team06.domain.review.dto

import com.back.nbe12142team06.domain.review.entity.Review
import com.back.nbe12142team06.domain.review.entity.ReviewTag
import java.time.LocalDateTime

// 리뷰 조회용
data class ReviewDto(
    val id: Long?,
    val applicationId: Long?,
    val rating: Int?,
    val tags: Set<ReviewTag>,
    val content: String?,
    val createdAt: LocalDateTime?,
) {
    constructor(review: Review) : this(
        review.id,
        review.application.id,
        review.rating,
        // 원본 Java의 Set.copyOf() 동작을 유지한다.
        // 엔티티 컬렉션을 복사해 트랜잭션 종료 후의 지연 로딩 참조를 끊는다.
        java.util.Set.copyOf(review.tags),
        review.content,
        review.createdAt,
    )
}
