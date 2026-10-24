package com.back.nbe12142team06.domain.chat.repository

import com.back.nbe12142team06.domain.chat.dto.UnreadCountDto
import com.back.nbe12142team06.domain.chat.entity.ChatMessage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    @Query(
        "select m from ChatMessage m " +
                "join fetch m.chatPart cp " +
                "join fetch cp.user u " +
                "where cp.chatRoom.id=:chatRoomId"
    )
    fun findByChatRoomId(
        @Param("chatRoomId") chatRoomId: Long,
        pageable: Pageable
    ): Page<ChatMessage>

    @Query(
        "select cp.chatRoom.id, count(m) " +
                "from ChatPart cp " +
                "left join ChatMessage m on m.chatPart.chatRoom.id=cp.chatRoom.id " +
                "and m.chatPart.user.id <> cp.user.id " +
                "and m.sentAt > cp.lastReadAt " +
                "where cp.user.id=:userId " +
                "and cp.chatRoom.id in :chatRoomIds " +
                "group by cp.chatRoom.id"
    )
    fun getUnreadCountsByChatRoomIds(
        @Param("userId") userId: Long,
        @Param("chatRoomIds") chatRoomIds: List<Long>
    ): List<UnreadCountDto>
}
