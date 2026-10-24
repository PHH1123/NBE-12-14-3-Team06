package com.back.nbe12142team06.domain.chat.repository

import com.back.nbe12142team06.domain.chat.entity.ChatPart
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatPartRepository : JpaRepository<ChatPart, Long> {
    fun findByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): ChatPart?

    @Query(
        "select c from ChatPart c " +
                "join fetch c.user u " +
                "where u.id=:userId and c.chatRoom.id=:chatRoomId"
    )
    fun fetchJoinByUserIdAndChatRoomId(
        @Param("userId") userId: Long,
        @Param("chatRoomId") chatRoomId: Long
    ): ChatPart?

    fun findByUserIdNotAndChatRoomId(userId: Long, chatRoomId: Long): List<ChatPart>
}
