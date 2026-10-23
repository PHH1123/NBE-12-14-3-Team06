package com.back.nbe12142team06.domain.chat.dto;

import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.global.exception.NotFoundException;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long chatRoomId,
        Long partnerId,
        String partnerName,
        Role partnerRole,
        String lastChatContent,
        LocalDateTime lastChatSentAt,
        Long unReadCount
) {
    public ChatRoomResponse(ChatRoom chatRoom, Long userId, Long unReadCount) {
        ChatPart chatPart = chatRoom.getChatParts().stream()
                .filter((cp -> !cp.getUser().getId().equals(userId)))
                .findFirst().orElseThrow(() -> new NotFoundException("상대방을 찾을 수 없습니다."));
        this(
                chatRoom.getId(),
                chatPart.getUser().getId(),
                chatPart.getUser().getName(),
                chatPart.getRole(),
                chatRoom.getLastChatContent(),
                chatRoom.getLastChatSentAt(),
                unReadCount
        );
    }
}
