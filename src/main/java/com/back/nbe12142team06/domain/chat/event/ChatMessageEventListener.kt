package com.back.nbe12142team06.domain.chat.event

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ChatMessageEventListener(
    private val messagingTemplate: SimpMessagingTemplate
) {

    @TransactionalEventListener
    fun sendEvent(event: ChatMessageEvent) {
        messagingTemplate.convertAndSend(
            CHAT_SUBSCRIBE_PATH + event.chatRoomId,
            event.chatMessageResponse
        )
    }

    companion object {
        private const val CHAT_SUBSCRIBE_PATH = "/topic/chat/"
    }
}
