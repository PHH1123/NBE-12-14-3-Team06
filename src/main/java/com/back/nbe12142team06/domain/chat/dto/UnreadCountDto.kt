package com.back.nbe12142team06.domain.chat.dto

@JvmRecord
data class UnreadCountDto(
    val chatRoomId: Long,
    val count: Long
)
