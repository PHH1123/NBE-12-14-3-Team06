package com.back.nbe12142team06.domain.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {
    ENTER("방 입장"),
    LEAVE("방 나가기"),
    TALK("이야기");

    private final String description;
}
