package com.back.nbe12142team06.domain.chat.repository;

import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatPartRepository extends JpaRepository<ChatPart, Long> {
    Optional<ChatPart> findByUserIdAndChatRoomId(Long userId, Long chatRoomId);

    List<ChatPart> findByUserIdNotAndChatRoomId(Long userId, Long chatRoomId);
}
