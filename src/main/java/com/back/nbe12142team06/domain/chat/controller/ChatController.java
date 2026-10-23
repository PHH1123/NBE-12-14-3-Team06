package com.back.nbe12142team06.domain.chat.controller;

import com.back.nbe12142team06.domain.chat.dto.ChatMessageDto;
import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse;
import com.back.nbe12142team06.domain.chat.dto.ChatRoomResponse;
import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.chat.service.ChatService;
import com.back.nbe12142team06.global.response.RsData;
import com.back.nbe12142team06.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Tag(name = "채팅", description = "채팅 구독, 전송, 조회, 읽기 관련 API")
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "주제 구독",
            description = "채팅을 실시간으로 읽기 위해 채팅방을 구독합니다."
    )
    @MessageMapping("/chat/{chatRoomId}")
    public void handle(@DestinationVariable Long chatRoomId,
                       @Payload ChatMessageDto dto,
                       Principal principal) {
        Authentication auth = (Authentication) principal;
        SecurityUser user = (SecurityUser) auth.getPrincipal();
        chatService.sendMessage(chatRoomId, user.getId(), dto.content());
    }

    @Operation(
            summary = "채팅방 생성",
            description = "채팅방을 생성합니다."
    )
    @PostMapping("/api/v1/chat")
    @ResponseBody
    public RsData<ChatRoomResponse> createChatRoom(@AuthenticationPrincipal SecurityUser user,
                                                   @RequestParam(name = "user-id") Long receiverId) {
        Long senderId = user.getId();
        ChatRoom chatRoom = chatService.createChatRoom(senderId, receiverId);

        return new RsData<>("201-121", "채팅방이 생성되었습니다.",
                new ChatRoomResponse(chatRoom, user.getId(), 0L));
    }

    @Operation(
            summary = "채팅방 목록 조회",
            description = "채팅방 목록을 조회합니다."
    )
    @GetMapping("/api/v1/chat")
    @ResponseBody
    public RsData<List<ChatRoomResponse>> list(@AuthenticationPrincipal SecurityUser user) {
        Long userId = user.getId();

        List<ChatRoom> chatRoomList = chatService.getChatRoomList(userId);
        Map<Long, Long> unReadMap = chatService.getUnReadCounts(userId, chatRoomList);

        return new RsData<>("200-121", "채팅방 목록을 조회했습니다.",
                chatRoomList.stream()
                        .map(chatRoom -> new ChatRoomResponse(
                                chatRoom, userId, unReadMap.getOrDefault(chatRoom.getId(), 0L))).toList());
    }

    @Operation(
            summary = "채팅 메시지 조회",
            description = "채팅 메시지를 조회합니다."
    )
    @GetMapping("/api/v1/chat/{chatRoomId}")
    @ResponseBody
    public RsData<Page<ChatMessageResponse>> getMessage(@AuthenticationPrincipal SecurityUser user,
                                                        @PathVariable Long chatRoomId,
                                                        @RequestParam(required = false, defaultValue = "0") int page,
                                                        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Long userId = user.getId();

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");

        Page<ChatMessage> messages = chatService.getMessages(userId, chatRoomId, pageable);

        return new RsData<>("200-122", "채팅 메시지를 조회했습니다.",
                messages.map(ChatMessageResponse::new));
    }

    @Operation(
            summary = "채팅방 퇴장",
            description = "채팅방을 퇴장합니다."
    )
    @PatchMapping("/api/v1/chat/{chatRoomId}")
    @ResponseBody
    public RsData<?> leaveChatRoom(@AuthenticationPrincipal SecurityUser user,
                                   @PathVariable Long chatRoomId) {

        chatService.leaveChatRoom(user.getId(), chatRoomId);

        return new RsData<>("204-121", "채팅방을 떠났습니다.");
    }
}
