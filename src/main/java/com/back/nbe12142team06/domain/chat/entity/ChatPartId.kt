package com.back.nbe12142team06.domain.chat.entity

import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class ChatPartId(
    userId: Long? = null,
    chatRoomId: Long? = null,
) : Serializable {
    var userId: Long? = userId
        protected set
    var chatRoomId: Long? = chatRoomId
        protected set
}
