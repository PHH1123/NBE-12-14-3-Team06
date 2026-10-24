package com.back.nbe12142team06.domain.chat.dto

import com.back.nbe12142team06.domain.chat.entity.ChatMessage
import com.back.nbe12142team06.domain.chat.entity.MessageType
import java.time.LocalDateTime

@JvmRecord
data class ChatMessageResponse(
    val chatMessageId: Long,
    val senderId: Long,
    val senderName: String,
    val content: String,
    val sentAt: LocalDateTime,
    val type: MessageType
) {
    constructor(chatMessage: ChatMessage) : this(
        chatMessageId = chatMessage.requiredId,
        senderId = chatMessage.chatPart.user.id,
        senderName = chatMessage.chatPart.user.name,
        content = chatMessage.content,
        sentAt = chatMessage.sentAt,
        type = chatMessage.type
    )
}
