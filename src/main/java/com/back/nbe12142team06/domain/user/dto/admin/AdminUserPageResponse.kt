package com.back.nbe12142team06.domain.user.dto.admin

import com.back.nbe12142team06.domain.user.entity.User
import org.springframework.data.domain.Page

@JvmRecord
data class AdminUserPageResponse(
    val content: List<AdminUserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean,
) {
    constructor(page: Page<User>) : this(
        page.content.map { AdminUserResponse(it) },
        page.number,
        page.size,
        page.totalElements,
        page.totalPages,
        page.isLast,
    )
}