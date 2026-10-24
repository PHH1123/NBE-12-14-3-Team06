package com.back.nbe12142team06.domain.notification.dto

import com.back.nbe12142team06.domain.notification.entity.Notification
import java.time.LocalDateTime

@JvmRecord
data class NotificationResponse(
    val id: Long,
    val content: String,
    val url: String,
    val isRead: Boolean,
    val userId: Long,
    val createdAt: LocalDateTime
) {
    constructor(notification: Notification) : this(
        notification.id,
        notification.content,
        notification.url,
        notification.isRead,
        notification.receiver.id,
        notification.createdAt!!
    )
}
