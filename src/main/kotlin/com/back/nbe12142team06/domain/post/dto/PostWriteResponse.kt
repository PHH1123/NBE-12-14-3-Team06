package com.back.nbe12142team06.domain.post.dto

import com.back.nbe12142team06.domain.post.entity.Post
import java.time.LocalDateTime

@JvmRecord
data class PostWriteResponse(
    val id: Long?,
    val title: String,
    val postStatus: String,
    val createdAt: LocalDateTime?,
    val paymentId: Long?,
) {
    constructor(post: Post, paymentId: Long?) : this(
        post.id,
        post.title,
        post.postStatus.description,
        post.createdAt,
        paymentId,
    )
}
