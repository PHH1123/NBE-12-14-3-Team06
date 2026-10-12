package com.back.nbe12142team06.domain.chat.service;

import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse;
import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.chat.entity.MessageType;
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository;
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    private static final String CHAT_SUBSCRIBE_PATH = "/topic/chat/";

    @Transactional
    public void sendMessage(Long chatRoomId, String content, ChatPart chatPart) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(122, "존재하지 않는 채팅방입니다."));

        // 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatPart(chatPart)
                .content(content)
                .type(MessageType.TALK)
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);
        chatRoom.updateLastChat(content);

        // 메시지 전송
        messagingTemplate.convertAndSend(CHAT_SUBSCRIBE_PATH + chatRoomId,
                new ChatMessageResponse(chatMessage));
    }

    public Page<ChatMessage> getMessages(Long chatRoomId, Pageable pageable) {
        return chatMessageRepository.findByChatRoomId(chatRoomId, pageable);
    }

    @Transactional
    public void createRoomMessage(String content, ChatPart chatPart) {
        // 채팅방 생성 안내 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatPart(chatPart)
                .content(content)
                .type(MessageType.ENTER)
                .build();
        chatMessageRepository.save(chatMessage);
    }
}
