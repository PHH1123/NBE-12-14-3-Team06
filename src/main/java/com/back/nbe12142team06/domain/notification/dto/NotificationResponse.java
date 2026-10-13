package com.back.nbe12142team06.domain.notification.dto;

import com.back.nbe12142team06.domain.notification.entity.Notification;

public record NotificationResponse(
        Long id,
        String content,
        String url,
        Boolean isRead,
        Long userId,
        String name
) {
    public NotificationResponse(Notification notification) {
        this(
                notification.getId(),
                notification.getContent(),
                notification.getUrl(),
                notification.isRead(),
                notification.getReceiver().getId(),
                notification.getReceiver().getName()
        );
    }
}
