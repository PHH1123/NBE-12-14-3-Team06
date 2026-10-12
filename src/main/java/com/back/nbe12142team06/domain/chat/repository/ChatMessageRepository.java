package com.back.nbe12142team06.domain.chat.repository;

import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m " +
            "join fetch m.chatPart cp " +
            "join fetch cp.user u " +
            "where cp.chatRoom.id=:chatRoomId")
    Page<ChatMessage> findByChatRoomId(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("select count(m) from ChatMessage m " +
            "where m.chatPart.user.id<>:userId " +
            "and m.chatPart.chatRoom.id=:chatRoomId " +
            "and m.sentAt > :lastReadAt")
    int getUnReadCounts(@Param("userId") Long userId,
                        @Param("chatRoomId") Long chatRoomId,
                        @Param("lastReadAt") LocalDateTime lastReadAt);
}
