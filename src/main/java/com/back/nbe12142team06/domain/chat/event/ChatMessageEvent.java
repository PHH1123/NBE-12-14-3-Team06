package com.back.nbe12142team06.domain.chat.event;

import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse;

public record ChatMessageEvent(
        String path,
        Long chatRoomId,
        ChatMessageResponse chatMessageResponse
) {
}
