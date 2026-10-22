package com.back.nbe12142team06.domain.chat.service;

import com.back.nbe12142team06.domain.chat.dto.UnreadCountDto;
import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository;
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.service.UserService;
import com.back.nbe12142team06.global.exception.DuplicatedException;
import com.back.nbe12142team06.global.exception.ForbiddenException;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserService userService;
    private final ChatMessageService chatMessageService;
    private final ChatPartRepository chatPartRepository;

    private final String CREATE_ROOM_MESSAGE = "님이 채팅방을 개설했습니다.";
    private final String LEAVE_ROOM_MESSAGE = "님이 채팅방을 떠났습니다.";

    // 채팅방 생성
    @Transactional
    public ChatRoom createChatRoom(Long senderId, Long receiverId) {
        User sender = userService.myProfile(senderId);
        User receiver = userService.myProfile(receiverId);

        // 이미 해당 유저들의 채팅방이 있는지 검사
        Optional<ChatRoom> findChatRoom = chatRoomRepository.findByUser(sender, receiver);
        if (findChatRoom.isPresent()) {
            ChatRoom chatRoom = findChatRoom.get();
            chatRoom.getChatParts()
                    .forEach(part -> {
                        if (part.getUser().getId().equals(senderId)) {
                            part.read();
                        }
                    });
            return findChatRoom.get();
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .lastChatContent(sender.getName() + CREATE_ROOM_MESSAGE)
                .lastChatSentAt(LocalDateTime.now())
                .build();
        ChatPart chatPart = chatRoom.addChatPart(sender, receiver);
        chatRoom = chatRoomRepository.save(chatRoom);

        // 채팅방 개설 메시지 저장
        ChatMessage chatMessage = chatMessageService.createRoomMessage(sender.getName() + CREATE_ROOM_MESSAGE, chatPart);
        chatRoom.updateLastChat(chatMessage.getContent(), chatMessage.getSentAt());

        return chatRoom;
    }

    // 채팅방 나가기
    @Transactional
    public void leaveChatRoom(Long userId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(122, "존재하지 않는 채팅방입니다."));
        ChatPart chatPart = chatPartRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new NotFoundException(121, "채팅 입장 내역이 없습니다."));

        if (!chatPart.getIsActive()) {
            throw new InvalidException(122, "이미 떠난 채팅방입니다.");
        }

        chatPart.leave();
        ChatMessage chatMessage = chatMessageService.leaveRoomMessage(chatPart.getUser().getName() + LEAVE_ROOM_MESSAGE, chatPart, chatRoomId);

        chatRoom.updateLastChat(chatMessage.getContent(), chatMessage.getSentAt());
    }

    // 채팅 보내기
    @Transactional
    public ChatMessage sendMessage(Long chatRoomId, Long senderId, String content) {
        // TODO: Fetch Join 적용
        ChatPart chatPart = chatPartRepository.findByUserIdAndChatRoomId(senderId, chatRoomId)
                .orElseThrow(() -> new NotFoundException(121, "채팅 입장 내역이 없습니다."));

        ChatPart receiverChatPart = chatPartRepository.findByUserIdNotAndChatRoomId(senderId, chatRoomId).getFirst();
        if (!receiverChatPart.getIsActive()) {
            receiverChatPart.join();
        }

        return chatMessageService.sendMessage(chatRoomId, content, chatPart);
    }

    // 채팅방 목록 조회
    @Transactional(readOnly = true)
    public List<ChatRoom> getChatRoomList(Long userId) {
        return chatRoomRepository.findByUser(userId);
    }

    // 채팅 메시지 조회
    @Transactional
    public Page<ChatMessage> getMessages(Long userId, Long chatRoomId, Pageable pageable) {
        ChatPart chatPart = chatPartRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new ForbiddenException(121, "채팅방에 접속하실 권한이 없습니다."));

        if (!chatPart.getIsActive()) {
            throw new InvalidException(122, "이미 떠난 채팅방입니다.");
        }

        chatPart.read();
        return chatMessageService.getMessages(chatRoomId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getUnReadCounts(Long userId, List<ChatRoom> chatRooms) {
        if (chatRooms.isEmpty()) {
            return Map.of();
        }
        List<Long> chatRoomIds = chatRooms.stream().map(ChatRoom::getId).toList();

        return chatMessageService.getUnReadCounts(userId, chatRoomIds).stream()
                .collect(Collectors.toMap(UnreadCountDto::chatRoomId, UnreadCountDto::count));
    }
}
