package com.back.nbe12142team06.domain.notification.dto;

import com.back.nbe12142team06.domain.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String content,
        String url,
        Boolean isRead,
        Long userId,
        LocalDateTime createdAt
) {
    public NotificationResponse(Notification notification) {
        this(
                notification.getId(),
                notification.getContent(),
                notification.getUrl(),
                notification.isRead(),
                notification.getReceiver().getId(),
                notification.getCreatedAt()
        );
    }
}
