package com.back.nbe12142team06.domain.chat.service

import com.back.nbe12142team06.domain.chat.entity.ChatMessage
import com.back.nbe12142team06.domain.chat.entity.ChatPart
import com.back.nbe12142team06.domain.chat.entity.ChatRoom
import com.back.nbe12142team06.domain.chat.entity.MessageType
import com.back.nbe12142team06.domain.chat.event.ChatMessageEvent
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ChatServiceTest {

    private val createRoomMessage = "님이 채팅방을 개설했습니다."
    private val escortContent = "안녕하세요 저는 동행매니저 입니다."
    private val clientContent = "안녕하세요 저는 의뢰인 입니다."

    @field:Autowired private lateinit var chatService: ChatService
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var events: ApplicationEvents

    @field:MockitoSpyBean private lateinit var chatRoomRepository: ChatRoomRepository
    @field:MockitoSpyBean private lateinit var chatMessageRepository: ChatMessageRepository
    @field:MockitoSpyBean private lateinit var userService: UserService
    @field:MockitoSpyBean private lateinit var chatPartRepository: ChatPartRepository

    @field:MockitoBean private lateinit var messagingTemplate: SimpMessagingTemplate

    // id 가 null 이면 저장할 때 DB 가 채우고, 값을 주면 저장 없이 리플렉션으로 넣는다
    private fun createClient(id: Long? = 1L, name: String = "의뢰인"): User = User(
        "client01", "password1!", "client01@email.com", name,
        Role.CLIENT, Gender.MALE, LocalDate.of(1960, 11, 11), "010-1234-1234", "서울"
    ).also { if (id != null) setId(it, id) }

    private fun createEscort(id: Long? = 2L): User = User(
        "escort01", "password1!", "escort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
    ).also { if (id != null) setId(it, id) }

    // 저장하지 않은 엔티티에도 id 가 필요해서 리플렉션으로 넣는다
    private fun setId(user: User, id: Long) {
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
    }

    private fun createChatRoom(sender: User, id: Long): ChatRoom = ChatRoom(
        id = id,
        lastChatContent = sender.name + createRoomMessage,
        lastChatSentAt = LocalDateTime.now()
    )

    private fun createChatPart(user: User, chatRoom: ChatRoom): ChatPart = ChatPart(
        user = user,
        chatRoom = chatRoom,
        role = user.role
    )

    // 새 채팅방을 만드는 흐름에서 DB 를 타지 않도록 경계만 대역으로 바꾼다
    private fun stubNewChatRoom(vararg users: User) {
        users.forEach { doReturn(it).whenever(userService).myProfile(it.id) }
        doReturn(null).whenever(chatRoomRepository).findByUser(any(), any())
        doAnswer { it.arguments[0] }.whenever(chatRoomRepository).save(any())
        doAnswer { it.arguments[0] }.whenever(chatMessageRepository).save(any())
    }

    @Test
    @DisplayName("[ChatService] 채팅방 만들기 - 동행 매니저 -> 의뢰인")
    fun createChatRoomEscortToClient() {
        val client = createClient()
        val escort = createEscort()
        stubNewChatRoom(client, escort)

        val chatRoom = chatService.createChatRoom(escort.id, client.id)

        assertThat(chatRoom.lastChatContent).isEqualTo(escort.name + createRoomMessage)
        assertThat(chatRoom.lastChatSentAt).isBeforeOrEqualTo(LocalDateTime.now())

        assertThat(chatRoom.chatParts).allSatisfy { chatPart ->
            assertThat(chatPart.chatRoom).isEqualTo(chatRoom)
            assertThat(chatPart.isActive).isTrue()
        }
    }

    @Test
    @DisplayName("[ChatService] 채팅방 만들기")
    fun createChatRoomClientToClient() {
        val client1 = createClient(1L, "의뢰인1")
        val client2 = createClient(2L, "의뢰인2")
        stubNewChatRoom(client1, client2)

        val chatRoom = chatService.createChatRoom(client1.id, client2.id)

        assertThat(chatRoom.lastChatContent).isEqualTo(client1.name + createRoomMessage)
        assertThat(chatRoom.lastChatSentAt).isBeforeOrEqualTo(LocalDateTime.now())

        assertThat(chatRoom.chatParts).allSatisfy { chatPart ->
            assertThat(chatPart.chatRoom).isEqualTo(chatRoom)
            assertThat(chatPart.isActive).isTrue()
        }
    }

    @Test
    @DisplayName("[ChatService] 채팅방 목록 조회")
    fun getChatList() {
        val escort = userRepository.save(createEscort(id = null))
        val client = userRepository.save(createClient(id = null))

        chatService.createChatRoom(escort.id, client.id)

        val escortChatRoomList = chatService.getChatRoomList(escort.id)
        val clientChatRoomList = chatService.getChatRoomList(client.id)

        // 두 사람이 같은 채팅방을 본다
        assertThat(escortChatRoomList.first().id).isEqualTo(clientChatRoomList.first().id)
    }

    @Test
    @DisplayName("[ChatService] 메시지 전송")
    fun sendMessage() {
        val escort = createEscort()
        val client = createClient()
        val chatRoom = createChatRoom(escort, 1L)

        val escortChatPart = createChatPart(escort, chatRoom)
        val clientChatPart = createChatPart(client, chatRoom)

        doReturn(escortChatPart)
            .whenever(chatPartRepository).fetchJoinByUserIdAndChatRoomId(eq(escort.id), any())
        doReturn(clientChatPart)
            .whenever(chatPartRepository).fetchJoinByUserIdAndChatRoomId(eq(client.id), any())
        doReturn(listOf(clientChatPart))
            .whenever(chatPartRepository).findByUserIdNotAndChatRoomId(eq(escort.id), any())
        doReturn(listOf(escortChatPart))
            .whenever(chatPartRepository).findByUserIdNotAndChatRoomId(eq(client.id), any())
        doReturn(Optional.of(chatRoom)).whenever(chatRoomRepository).findById(any())
        // 저장 시 id 가 붙은 메시지를 돌려준다
        doAnswer {
            val saved = it.arguments[0] as ChatMessage
            ChatMessage(1L, saved.chatPart, saved.content, saved.sentAt, saved.type)
        }.whenever(chatMessageRepository).save(any())

        val escortChatMessage = chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        val clientChatMessage = chatService.sendMessage(chatRoom.requiredId, client.id, clientContent)

        assertThat(escortChatMessage.type).isEqualTo(MessageType.TALK)
        assertThat(escortChatMessage.content).isEqualTo(escortContent)
        assertThat(escortChatMessage.sentAt).isBeforeOrEqualTo(LocalDateTime.now())

        assertThat(clientChatMessage.type).isEqualTo(MessageType.TALK)
        assertThat(clientChatMessage.content).isEqualTo(clientContent)
        assertThat(clientChatMessage.sentAt).isBeforeOrEqualTo(LocalDateTime.now())

        // 보낸 순서대로 전송 이벤트가 발행된다
        assertThat(events.stream(ChatMessageEvent::class.java))
            .extracting<String> { it.chatMessageResponse.content }
            .containsExactly(escortContent, clientContent)
    }

    @Test
    @DisplayName("[ChatService] 메시지 조회")
    fun getChatMessage() {
        val escort = userRepository.save(createEscort(id = null))
        val client = userRepository.save(createClient(id = null))

        val chatRoom = chatService.createChatRoom(escort.id, client.id)
        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        chatService.sendMessage(chatRoom.requiredId, client.id, clientContent)

        val pageable: Pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id")
        val escortMessages = chatService.getMessages(escort.id, chatRoom.requiredId, pageable).content
        val clientMessages = chatService.getMessages(client.id, chatRoom.requiredId, pageable).content

        // 최신 메시지가 먼저 오고, 마지막은 채팅방 개설 안내 메시지다
        assertThat(escortMessages.map { it.content })
            .containsExactly(clientContent, escortContent, escort.name + createRoomMessage)
        assertThat(clientMessages.map { it.content })
            .containsExactly(clientContent, escortContent, escort.name + createRoomMessage)
    }
}
