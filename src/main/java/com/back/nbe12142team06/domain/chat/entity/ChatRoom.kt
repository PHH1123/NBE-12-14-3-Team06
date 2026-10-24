package com.back.nbe12142team06.domain.chat.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*
import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Getter
import lombok.NoArgsConstructor
import java.time.LocalDateTime

@Entity
class ChatRoom(
    id: Long? = null,
    lastChatContent: String? = null,
    lastChatSentAt: LocalDateTime? = null,
    isActive: Boolean = true,
    @OneToMany(mappedBy = "chatRoom", cascade = [CascadeType.ALL], orphanRemoval = true)
    val chatParts: MutableList<ChatPart> = mutableListOf()
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    @Column(columnDefinition = "TEXT")
    var lastChatContent: String? = lastChatContent
        protected set

    var lastChatSentAt: LocalDateTime? = lastChatSentAt
        protected set

    @Column(nullable = false)
    var isActive = isActive
        protected set

    val requiredId: Long
        get() = id ?: error("ID가 NULL입니다.")

    // 채팅방 입장 내역 추가
    fun addChatPart(sender: User, receiver: User): ChatPart {
        val chatPart = ChatPart(
            user = sender,
            chatRoom = this,
            role = sender.role
        )
        chatParts.add(chatPart)
        chatParts.add(
            ChatPart(
                user = receiver,
                chatRoom = this,
                role = receiver.role
            )
        )
        return chatPart
    }

    // 마지막 채팅 업데이트
    fun updateLastChat(content: String, sentAt: LocalDateTime) {
        this.lastChatContent = content
        this.lastChatSentAt = sentAt
    }
}
