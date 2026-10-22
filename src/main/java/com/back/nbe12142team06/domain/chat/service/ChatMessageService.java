package com.back.nbe12142team06.domain.chat.service;

import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse;
import com.back.nbe12142team06.domain.chat.dto.UnreadCountDto;
import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.chat.entity.MessageType;
import com.back.nbe12142team06.domain.chat.event.ChatMessageEvent;
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository;
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatMessage sendMessage(Long chatRoomId, String content, ChatPart chatPart) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(122, "존재하지 않는 채팅방입니다."));

        // 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatPart(chatPart)
                .content(content)
                .type(MessageType.TALK)
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);
        chatRoom.updateLastChat(chatMessage.getContent(), chatMessage.getSentAt());

        // 메시지 전송
        eventPublisher.publishEvent(
                new ChatMessageEvent(chatRoomId, new ChatMessageResponse(chatMessage)));

        return chatMessage;
    }

    public Page<ChatMessage> getMessages(Long chatRoomId, Pageable pageable) {
        return chatMessageRepository.findByChatRoomId(chatRoomId, pageable);
    }

    @Transactional
    public ChatMessage createRoomMessage(String content, ChatPart chatPart) {
        // 채팅방 생성 안내 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatPart(chatPart)
                .content(content)
                .type(MessageType.ENTER)
                .build();
        return chatMessageRepository.save(chatMessage);
    }

    public List<UnreadCountDto> getUnReadCounts(Long userId, List<Long> chatRoomIds) {
        return chatMessageRepository.getUnreadCountsByChatRoomIds(userId, chatRoomIds);
    }

    @Transactional
    public ChatMessage leaveRoomMessage(String content, ChatPart chatPart, Long chatRoomId) {
        // 채팅방 나가기 안내 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatPart(chatPart)
                .content(content)
                .type(MessageType.LEAVE)
                .build();
        chatMessage = chatMessageRepository.save(chatMessage);

        // 채팅방 나갔을 때는 상대방이 채팅 중일 수 있으므로 메시지 전송
        eventPublisher.publishEvent(
                new ChatMessageEvent(chatRoomId, new ChatMessageResponse(chatMessage)));

        return chatMessage;
    }
}
