package com.back.nbe12142team06.domain.chat.entity

enum class MessageType(
    val description: String
) {
    ENTER("방 입장"),
    LEAVE("방 나가기"),
    TALK("이야기");
}
