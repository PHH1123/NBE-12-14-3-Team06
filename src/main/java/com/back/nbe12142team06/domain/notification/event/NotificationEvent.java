package com.back.nbe12142team06.domain.notification.event;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.user.entity.User;

public record NotificationEvent(
        String content,
        String url,
        User receiver,
        Notification notification
) {
}
