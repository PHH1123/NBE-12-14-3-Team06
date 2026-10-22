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
    private static final String CHAT_SUBSCRIBE_PATH = "/topic/chat/";

    @TransactionalEventListener
    public void sendEvent(ChatMessageEvent event) {
        messagingTemplate.convertAndSend(CHAT_SUBSCRIBE_PATH + event.chatRoomId(),
                event.chatMessageResponse());
    }
}
