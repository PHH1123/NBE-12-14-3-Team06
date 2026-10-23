package com.back.nbe12142team06.domain.post.dto

import java.time.LocalDateTime

@JvmRecord
data class PostSearchConditionDto(
    val keyword: String?,
    val region: String?,
    val dateFrom: LocalDateTime?,
    val dateTo: LocalDateTime?,
    val minPay: Int?,
    val maxPay: Int?,
    val openOnly: Boolean,  // true = 모집중 탭(OPEN만), false = 마감 탭(OPEN 아닌 것만)
)
