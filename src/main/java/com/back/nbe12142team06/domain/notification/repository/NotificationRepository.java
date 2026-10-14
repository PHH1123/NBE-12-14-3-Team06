package com.back.nbe12142team06.domain.notification.repository;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n join fetch n.receiver r where r.id=:receiverId and n.deletedAt is null")
    Page<Notification> findByReceiverId(@Param("receiverId") Long receiverId, Pageable pageable);

    Optional<Notification> findByIdAndDeletedAtIsNull(Long id);
}
