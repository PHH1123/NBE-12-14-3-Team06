package com.back.nbe12142team06.domain.chat.dto;

import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long chatMessageId,
        Long senderId,
        String senderName,
        String content,
        LocalDateTime sentAt,
        MessageType type
) {
    public ChatMessageResponse(ChatMessage chatMessage) {
        this(
                chatMessage.getId(),
                chatMessage.getChatPart().getUser().getId(),
                chatMessage.getChatPart().getUser().getName(),
                chatMessage.getContent(),
                chatMessage.getSentAt(),
                chatMessage.getType());
    }
}
