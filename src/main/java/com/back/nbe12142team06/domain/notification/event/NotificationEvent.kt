package com.back.nbe12142team06.domain.notification.event

import com.back.nbe12142team06.domain.notification.entity.Notification

@JvmRecord
data class NotificationEvent(
    val content: String,
    val url: String,
    val receiverId: Long,
    val notification: Notification
)
