package com.back.nbe12142team06.domain.notification.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class Notification(
    id: Long? = null,
    content: String,
    url: String? = null,
    isRead: Boolean = false,
    receiver: User,
) : BaseSoftDeleteTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String = content
        protected set

    var url: String? = url
        protected set

    @Column(nullable = false)
    var isRead = isRead
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    var receiver: User = receiver
        protected set

    val requiredId: Long
        get() = id ?: error("ID가 NULL입니다.")

    fun read() {
        this.isRead = true
    }

    fun delete() {
        deletedAt = LocalDateTime.now()
    }
}
