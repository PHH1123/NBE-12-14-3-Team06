package com.back.nbe12142team06.domain.chat.entity

import jakarta.persistence.Embeddable
import org.hibernate.proxy.HibernateProxy
import java.io.Serializable
import java.util.Objects

@Embeddable
class ChatPartId(
    userId: Long? = null,
    chatRoomId: Long? = null,
) : Serializable {
    var userId: Long? = userId
        protected set
    var chatRoomId: Long? = chatRoomId
        protected set

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            if (other is HibernateProxy) other.hibernateLazyInitializer.persistentClass else other.javaClass
        val thisEffectiveClass =
            if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass else this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as ChatPartId

        return userId != null && userId == other.userId
                && chatRoomId != null && chatRoomId == other.chatRoomId
    }

    final override fun hashCode(): Int = Objects.hash(userId, chatRoomId)
}
