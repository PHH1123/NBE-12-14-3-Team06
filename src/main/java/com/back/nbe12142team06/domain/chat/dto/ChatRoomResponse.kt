package com.back.nbe12142team06.domain.chat.dto

import com.back.nbe12142team06.domain.chat.entity.ChatRoom
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.NotFoundException
import java.time.LocalDateTime

@JvmRecord
data class ChatRoomResponse(
    val chatRoomId: Long,
    val partnerId: Long,
    val partnerName: String,
    val partnerRole: Role,
    val lastChatContent: String,
    val lastChatSentAt: LocalDateTime,
    val unReadCount: Long,
) {
    companion object {
        @JvmStatic
        fun from(chatRoom: ChatRoom, userId: Long, unReadCount: Long) : ChatRoomResponse {
            val chatPart = chatRoom.chatParts.firstOrNull { it.user.id != userId }
                ?: throw NotFoundException("상대방을 찾을 수 없습니다.")
            return ChatRoomResponse(
                chatRoomId = chatRoom.id,
                partnerId = chatPart.user.id,
                partnerName = chatPart.user.name,
                partnerRole = chatPart.role,
                lastChatContent = chatRoom.lastChatContent,
                lastChatSentAt = chatRoom.lastChatSentAt,
                unReadCount = unReadCount
            )
        }
    }
}
