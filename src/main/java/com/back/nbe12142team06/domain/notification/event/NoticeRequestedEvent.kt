package com.back.nbe12142team06.domain.notification.event

data class NoticeRequestedEvent(
    val content: String,
    val url: String?,
    val receiverId: Long
)
