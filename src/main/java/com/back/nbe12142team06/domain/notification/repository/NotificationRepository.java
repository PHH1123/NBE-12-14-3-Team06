package com.back.nbe12142team06.domain.notification.repository;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
