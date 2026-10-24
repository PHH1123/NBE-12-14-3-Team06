package com.back.nbe12142team06.domain.chat.event

import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse

@JvmRecord
data class ChatMessageEvent(
    val chatRoomId: Long,
    val chatMessageResponse: ChatMessageResponse
)
