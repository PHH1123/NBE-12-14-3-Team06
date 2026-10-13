package com.back.nbe12142team06.domain.chat.repository;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("select cr from ChatRoom cr " +
            "join fetch cr.chatParts cp " +
            "join fetch cp.user " +
            "where cr.id in" +
            "(select cr2.id from ChatRoom cr2 join cr2.chatParts cp2 " +
            "where cp2.user in(:user1, :user2) group by cr2.id having count(distinct cp2.user) = 2)")
    Optional<ChatRoom> findByUser(@Param("user1") User user1,
                                    @Param("user2") User user2);

    @Query("select cr from ChatRoom cr " +
            "join fetch cr.chatParts cp " +
            "join fetch cp.user u " +
            "where cr.id in" +
            "(select cr2.id from ChatRoom cr2 join cr2.chatParts cp2 where cp2.user.id=:userId and cp2.isActive=true)")
    List<ChatRoom> findByUser(@Param("userId") Long userId);
}
