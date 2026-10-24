package com.back.nbe12142team06.domain.chat.service

import com.back.nbe12142team06.domain.chat.dto.ChatMessageResponse
import com.back.nbe12142team06.domain.chat.dto.UnreadCountDto
import com.back.nbe12142team06.domain.chat.entity.ChatMessage
import com.back.nbe12142team06.domain.chat.entity.ChatPart
import com.back.nbe12142team06.domain.chat.entity.MessageType
import com.back.nbe12142team06.domain.chat.event.ChatMessageEvent
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository
import com.back.nbe12142team06.global.exception.NotFoundException
import lombok.RequiredArgsConstructor
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.function.Supplier

@Service
@Transactional(readOnly = true)
class ChatMessageService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun sendMessage(chatRoomId: Long, content: String, chatPart: ChatPart): ChatMessage {
        val chatRoom = chatRoomRepository.findByIdOrNull(chatRoomId)
            ?: throw NotFoundException(122, "존재하지 않는 채팅방입니다.")

        // 메시지 저장
        val chatMessage = chatMessageRepository.save(
            ChatMessage(
                chatPart = chatPart,
                content = content,
                type = MessageType.TALK
            )
        ).also {
            chatRoom.updateLastChat(it.content, it.sentAt)
        }

        // 메시지 전송
        eventPublisher.publishEvent(
            ChatMessageEvent(chatRoomId, ChatMessageResponse(chatMessage))
        )

        return chatMessage
    }

    fun getMessages(chatRoomId: Long, pageable: Pageable): Page<ChatMessage> =
        chatMessageRepository.findByChatRoomId(chatRoomId, pageable)

    // 채팅방 생성 안내 메시지 저장
    @Transactional
    fun createRoomMessage(content: String, chatPart: ChatPart): ChatMessage =
        chatMessageRepository.save(
            ChatMessage(
                chatPart = chatPart,
                content = content,
                type = MessageType.ENTER
            )
        )

    fun getUnReadCounts(userId: Long, chatRoomIds: List<Long>): List<UnreadCountDto> =
        chatMessageRepository.getUnreadCountsByChatRoomIds(userId, chatRoomIds)

    // 채팅방 나가기 안내 메시지 저장
    @Transactional
    fun leaveRoomMessage(content: String, chatPart: ChatPart, chatRoomId: Long): ChatMessage =
        chatMessageRepository.save(
            ChatMessage(
                chatPart = chatPart,
                content = content,
                type = MessageType.LEAVE
            )
        ).also {
            // 채팅방 나갔을 때는 상대방이 채팅 중일 수 있으므로 메시지 전송
            eventPublisher.publishEvent(
                ChatMessageEvent(chatRoomId, ChatMessageResponse(it))
            )
        }
}
