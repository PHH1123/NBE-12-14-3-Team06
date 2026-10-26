package com.back.nbe12142team06.domain.post.dto

import com.back.nbe12142team06.domain.post.entity.Post
import java.math.BigDecimal
import java.time.LocalDateTime

data class PostDto(
    val id: Long?,
    val clientUserId: Long?,
    val client_id: String,
    val isOwner: Boolean,
    val title: String,
    val content: String,
    val patientNote: String?,
    val region: String,
    val hospitalName: String,
    val hospitalAddress: String,
    val hospitalLat: BigDecimal,
    val hospitalLng: BigDecimal,
    val pickupAddress: String,
    val pickupLat: BigDecimal,
    val pickupLng: BigDecimal,
    val hourlyPay: Int,
    val recruitStartAt: LocalDateTime,
    val recruitEndAt: LocalDateTime,
    val escortStartAt: LocalDateTime,
    val escortEndAt: LocalDateTime,
    val escortHours: BigDecimal,
    val totalPay: BigDecimal,
    val postStatus: String,
    val reportRequired: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val canViewSensitive: Boolean,  // 민감정보 권한
) {
    // Java 호출부(new PostDto(post), (post, canViewSensitive))도 그대로 쓸 수 있게 오버로드를 만든다
    constructor(post: Post, canViewSensitive: Boolean = true, isOwner: Boolean = false) : this(
        post.id,
        post.client.id,
        maskName(post.client.name),
        isOwner,
        post.title,
        post.content,
        if (canViewSensitive) post.patientNote else null,
        post.region,
        post.hospitalName,
        post.hospitalAddress,
        post.hospitalLat,
        post.hospitalLng,
        post.pickupAddress,
        post.pickupLat,
        post.pickupLng,
        post.hourlyPay,
        post.recruitStartAt,
        post.recruitEndAt,
        post.escortStartAt,
        post.escortEndAt,
        post.escortHours,
        post.totalPay,
        post.postStatus.description,
        post.reportRequired,
        post.createdAt,
        post.updatedAt,
        canViewSensitive,
    )

    companion object {
        // 이름은 화면에서만 마스킹한다. 원본 이름과 로그인 아이디는 변경하지 않는다.
        private fun maskName(name: String?): String {
            if (name.isNullOrBlank()) return "***"
            val chars = name.codePoints().toArray()
            if (chars.size < 2) return "*" // 예상하지 못한 데이터에 대한 방어 처리
            val first = String(chars, 0, 1)
            if (chars.size == 2) return "$first*"
            val last = String(chars, chars.size - 1, 1)
            return first + "*".repeat(chars.size - 2) + last
        }
    }
}
