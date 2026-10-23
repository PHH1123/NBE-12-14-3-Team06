package com.back.nbe12142team06.domain.chat.repository;

import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatPartRepository extends JpaRepository<ChatPart, Long> {
    Optional<ChatPart> findByUserIdAndChatRoomId(Long userId, Long chatRoomId);

    @Query("select c from ChatPart c join fetch c.user u where u.id=:userId and c.chatRoom.id=:chatRoomId")
    Optional<ChatPart> fetchJoinByUserIdAndChatRoomId(@Param("userId") Long userId,
                                                      @Param("chatRoomId") Long chatRoomId);

    List<ChatPart> findByUserIdNotAndChatRoomId(Long userId, Long chatRoomId);
}
