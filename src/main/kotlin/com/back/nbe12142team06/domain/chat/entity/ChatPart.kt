package com.back.nbe12142team06.domain.chat.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class ChatPart(
    id: ChatPartId? = null,
    user: User,
    chatRoom: ChatRoom,
    role: Role,
    isActive: Boolean = true,
    lastReadAt: LocalDateTime = LocalDateTime.now()
) : BaseTimeEntity() {
    // 복합키
    @EmbeddedId
    var id: ChatPartId? = id
        protected set

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User = user
        protected set

    @MapsId("chatRoomId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    var chatRoom: ChatRoom = chatRoom
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = role
        protected set

    @Column(nullable = false)
    var isActive = isActive
        protected set

    @Column(nullable = false)
    var lastReadAt: LocalDateTime = lastReadAt
        protected set

    val requiredId: ChatPartId
        get() = id ?: error("ID가 NULL입니다.")

    fun read() {
        this.lastReadAt = LocalDateTime.now()
    }

    fun leave() {
        this.isActive = false
    }

    fun join() {
        this.isActive = true
    }
}
