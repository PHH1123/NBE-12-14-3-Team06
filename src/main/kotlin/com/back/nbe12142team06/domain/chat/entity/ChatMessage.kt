package com.back.nbe12142team06.domain.chat.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class ChatMessage(
    id: Long? = null,
    chatPart: ChatPart,
    content: String,
    sentAt: LocalDateTime = LocalDateTime.now(),
    type: MessageType,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
        JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false),
        JoinColumn(name = "chat_room_id", referencedColumnName = "chat_room_id", nullable = false)
    )
    var chatPart: ChatPart = chatPart
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set

    @Column(nullable = false)
    var sentAt: LocalDateTime = sentAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: MessageType = type
        protected set

    val requiredId: Long
        get() = id ?: error("ID가 NULL입니다.")
}
