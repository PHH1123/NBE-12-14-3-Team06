package com.back.nbe12142team06.domain.chat.dto;

import com.back.nbe12142team06.domain.chat.entity.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long chatRoomId,
        String lastChatContent,
        LocalDateTime lastChatSentAt
) {
    public ChatRoomResponse(ChatRoom chatRoom) {
        this(chatRoom.getId(), chatRoom.getLastChatContent(), chatRoom.getLastChatSentAt());
    }
}
