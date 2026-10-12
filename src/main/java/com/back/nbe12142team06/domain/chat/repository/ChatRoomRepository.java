package com.back.nbe12142team06.domain.chat.repository;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("select cr from ChatRoom cr join cr.chatParts cp where cp.user IN(:user1, :user2) group by cr.id having count(distinct cp.user) = 2")
    Optional<ChatRoom> findByUser(@Param("user1") User user1,
                                    @Param("user2") User user2);

    @Query("select cr from ChatRoom cr join cr.chatParts cp where cp.user.id=:userId")
    List<ChatRoom> findByUser(@Param("userId") Long userId);
}
