package com.back.nbe12142team06.domain.chat.dto;

public record UnreadCountDto(
        Long chatRoomId,
        Integer count
) {
}
