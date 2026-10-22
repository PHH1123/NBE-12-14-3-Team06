package com.back.nbe12142team06.domain.chat.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void sendEvent(ChatMessageEvent event) {
        messagingTemplate.convertAndSend(event.path() + event.chatRoomId(),
                event.chatMessageResponse());
    }
}
