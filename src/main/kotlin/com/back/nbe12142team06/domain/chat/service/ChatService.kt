package com.back.nbe12142team06.domain.chat.service

import com.back.nbe12142team06.domain.chat.entity.ChatMessage
import com.back.nbe12142team06.domain.chat.entity.ChatPart
import com.back.nbe12142team06.domain.chat.entity.ChatRoom
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val userService: UserService,
    private val chatMessageService: ChatMessageService,
    private val chatPartRepository: ChatPartRepository,
    private val userRepository: UserRepository
) {

    private val CREATE_ROOM_MESSAGE = "님이 채팅방을 개설했습니다."
    private val LEAVE_ROOM_MESSAGE = "님이 채팅방을 떠났습니다."

    // 채팅방 생성
    @Transactional
    fun createChatRoom(senderId: Long, receiverId: Long): ChatRoom {

        listOf(senderId, receiverId).sorted().forEach { id ->
            userRepository.findByIdWithLock(id)
                ?: throw NotFoundException(1, "회원 정보를 찾을 수 없습니다.")
        }

        val sender = userService.myProfile(senderId)
        val receiver = userService.myProfile(receiverId)

        // 이미 해당 유저들의 채팅방이 있는지 검사
        chatRoomRepository.findByUser(sender, receiver)?.let { chatRoom ->
            chatRoom.chatParts.firstOrNull { it.user.id == senderId }
                ?.read()
            return chatRoom
        }

        var chatRoom = ChatRoom(
            lastChatContent = sender.name + CREATE_ROOM_MESSAGE,
            lastChatSentAt = LocalDateTime.now()
        )
        val chatPart = chatRoom.addChatPart(sender, receiver)
        chatRoom = chatRoomRepository.save(chatRoom)

        // 채팅방 개설 메시지 저장
        val chatMessage = chatMessageService.createRoomMessage(sender.name + CREATE_ROOM_MESSAGE, chatPart)
        chatRoom.updateLastChat(chatMessage.content, chatMessage.sentAt)

        return chatRoom
    }

    // 채팅방 나가기
    @Transactional
    fun leaveChatRoom(userId: Long, chatRoomId: Long) {
        val chatRoom = chatRoomRepository.findByIdOrNull(chatRoomId)
            ?: throw NotFoundException(122, "존재하지 않는 채팅방입니다.")
        val chatPart: ChatPart = chatPartRepository.fetchJoinByUserIdAndChatRoomId(userId, chatRoomId)
            ?: throw NotFoundException(121, "채팅 입장 내역이 없습니다.")

        if (!chatPart.isActive) {
            throw InvalidException(122, "이미 떠난 채팅방입니다.")
        }

        chatPart.leave()
        val chatMessage =
            chatMessageService.leaveRoomMessage(
                content = chatPart.user.name + LEAVE_ROOM_MESSAGE,
                chatPart = chatPart,
                chatRoomId = chatRoomId
            )

        chatRoom.updateLastChat(chatMessage.content, chatMessage.sentAt)
    }

    // 채팅 보내기
    @Transactional
    fun sendMessage(chatRoomId: Long, senderId: Long, content: String): ChatMessage {
        val chatPart: ChatPart = chatPartRepository.fetchJoinByUserIdAndChatRoomId(senderId, chatRoomId)
            ?: throw NotFoundException(121, "채팅 입장 내역이 없습니다.")

        val receiverChatPart: ChatPart =
            chatPartRepository.findByUserIdNotAndChatRoomId(senderId, chatRoomId).first()

        if (!receiverChatPart.isActive) {
            receiverChatPart.join()
        }

        return chatMessageService.sendMessage(chatRoomId, content, chatPart)
    }

    // 채팅방 목록 조회
    @Transactional(readOnly = true)
    fun getChatRoomList(userId: Long): List<ChatRoom> {
        return chatRoomRepository.findByUser(userId)
    }

    // 채팅 메시지 조회
    @Transactional
    fun getMessages(userId: Long, chatRoomId: Long, pageable: Pageable): Page<ChatMessage> {
        val chatPart: ChatPart = chatPartRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            ?: throw ForbiddenException(121, "채팅방에 접속하실 권한이 없습니다.")

        if (!chatPart.isActive) {
            throw InvalidException(122, "이미 떠난 채팅방입니다.")
        }

        chatPart.read()
        return chatMessageService.getMessages(chatRoomId, pageable)
    }

    @Transactional(readOnly = true)
    fun getUnReadCounts(userId: Long, chatRooms: List<ChatRoom>): Map<Long, Long> {
        if (chatRooms.isEmpty()) {
            return mapOf()
        }
        val chatRoomIds: List<Long> = chatRooms.map { it.requiredId }.toList()

        return chatMessageService.getUnReadCounts(userId, chatRoomIds).associate { it.chatRoomId to it.count }
    }
}
