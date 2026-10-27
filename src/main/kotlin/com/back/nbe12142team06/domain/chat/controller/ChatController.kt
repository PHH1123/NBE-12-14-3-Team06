package com.back.nbe12142team06.domain.chat.controller

import com.back.nbe12142team06.domain.chat.dto.ChatMessageDto
import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse
import com.back.nbe12142team06.domain.chat.dto.ChatRoomResponse
import com.back.nbe12142team06.domain.chat.dto.ChatRoomResponse.Companion.from
import com.back.nbe12142team06.domain.chat.entity.ChatRoom
import com.back.nbe12142team06.domain.chat.service.ChatService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.security.Principal

@Tag(name = "채팅", description = "채팅 구독, 전송, 조회, 읽기 관련 API")
@Controller
class ChatController(
    private val chatService: ChatService
) {

    @Operation(summary = "주제 구독", description = "채팅을 실시간으로 읽기 위해 채팅방을 구독합니다.")
    @MessageMapping("/chat/{chatRoomId}")
    fun handle(
        @DestinationVariable chatRoomId: Long,
        @Payload dto: ChatMessageDto,
        principal: Principal
    ) {
        val auth = principal as Authentication
        val user = auth.principal as SecurityUser
        chatService.sendMessage(chatRoomId, user.id, dto.content)
    }

    @Operation(summary = "채팅방 생성", description = "채팅방을 생성합니다.")
    @PostMapping("/api/v1/chat")
    @ResponseBody
    fun createChatRoom(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestParam(name = "user-id") receiverId: Long
    ): RsData<ChatRoomResponse> = RsData(
        "201-121", "채팅방이 생성되었습니다.",
        from(
            chatRoom = chatService.createChatRoom(
                senderId = user.id,
                receiverId = receiverId
            ),
            userId = user.id,
            unReadCount = 0L
        )
    )

    @Operation(summary = "채팅방 목록 조회", description = "채팅방 목록을 조회합니다.")
    @GetMapping("/api/v1/chat")
    @ResponseBody
    fun list(@AuthenticationPrincipal user: SecurityUser): RsData<List<ChatRoomResponse>> {
        val userId = user.id
        val chatRoomList: List<ChatRoom> = chatService.getChatRoomList(userId)
        val unReadMap: Map<Long, Long> = chatService.getUnReadCounts(userId, chatRoomList)

        return RsData(
            "200-121", "채팅방 목록을 조회했습니다.",
            chatRoomList.map {
                from(
                    chatRoom = it,
                    userId = userId,
                    unReadCount = unReadMap.getOrDefault(it.requiredId, 0L)
                )
            }
        )
    }

    @Operation(summary = "채팅 메시지 조회", description = "채팅 메시지를 조회합니다.")
    @GetMapping("/api/v1/chat/{chatRoomId}")
    @ResponseBody
    fun getMessage(
        @AuthenticationPrincipal user: SecurityUser,
        @PathVariable chatRoomId: Long,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): RsData<Page<ChatMessageResponse>> = RsData(
        "200-122", "채팅 메시지를 조회했습니다.",
        chatService.getMessages(
            userId = user.id,
            chatRoomId = chatRoomId,
            pageable = PageRequest.of(
                page, size, Sort.Direction.DESC, "id"
            )
        ).map { ChatMessageResponse(it) }
    )

    @Operation(summary = "채팅방 퇴장", description = "채팅방을 퇴장합니다.")
    @PatchMapping("/api/v1/chat/{chatRoomId}")
    @ResponseBody
    fun leaveChatRoom(
        @AuthenticationPrincipal user: SecurityUser,
        @PathVariable chatRoomId: Long
    ): RsData<Void> {
        chatService.leaveChatRoom(user.id, chatRoomId)

        return RsData("204-121", "채팅방을 떠났습니다.")
    }
}
