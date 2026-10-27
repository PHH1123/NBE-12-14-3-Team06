package com.back.nbe12142team06.domain.chat.service

import com.back.nbe12142team06.domain.chat.entity.ChatRoom
import com.back.nbe12142team06.domain.chat.entity.MessageType
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 채팅방 생성 → 전송 → 조회(읽음) → 퇴장 → 재입장까지 실제 DB 로 상태 변화를 확인한다.
 * 안 읽은 메시지 수는 lastReadAt 과 맞물려 있어 같은 흐름에서 함께 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServiceLifecycleTest {

    private val createRoomMessage = "님이 채팅방을 개설했습니다."
    private val leaveRoomMessage = "님이 채팅방을 떠났습니다."
    private val escortContent = "안녕하세요 저는 동행매니저 입니다."
    private val clientContent = "안녕하세요 저는 의뢰인 입니다."

    @field:Autowired private lateinit var chatService: ChatService
    @field:Autowired private lateinit var chatRoomRepository: ChatRoomRepository
    @field:Autowired private lateinit var chatPartRepository: ChatPartRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var em: EntityManager

    // 실시간 전송은 외부(STOMP)로 나가므로 막아 둔다
    @field:MockitoBean private lateinit var messagingTemplate: SimpMessagingTemplate

    private lateinit var escort: User
    private lateinit var client: User
    private lateinit var stranger: User
    private lateinit var chatRoom: ChatRoom

    @BeforeEach
    fun setUp() {
        escort = userRepository.save(createUser("chatEscort", Role.ESCORT, "010-3333-0001"))
        client = userRepository.save(createUser("chatClient", Role.CLIENT, "010-3333-0002"))
        stranger = userRepository.save(createUser("chatStranger", Role.CLIENT, "010-3333-0003"))

        // 동행 매니저가 의뢰인에게 채팅방을 연다 (개설 안내 메시지 1건이 함께 쌓인다)
        chatRoom = chatService.createChatRoom(escort.id, client.id)
        em.flush()
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User = User(
        username, "password1!", "$username@test.test",
        if (role == Role.ESCORT) "동행매니저" else "의뢰인",
        role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
    )

    private fun pageable(): Pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id")

    private fun partOf(user: User) =
        chatPartRepository.findByUserIdAndChatRoomId(user.id, chatRoom.requiredId)!!

    private fun messageContents(reader: User): List<String> =
        chatService.getMessages(reader.id, chatRoom.requiredId, pageable()).content.map { it.content }

    // ===== 채팅방 생성 =====

    @Test
    @DisplayName("[ChatService] 이미 있는 채팅방은 새로 만들지 않고 그대로 돌려준다")
    fun createChatRoomReusesExistingRoom() {
        val before = partOf(escort).lastReadAt

        val again = chatService.createChatRoom(escort.id, client.id)
        em.flush()

        assertThat(again.requiredId).isEqualTo(chatRoom.requiredId)
        // 같은 두 사람의 방은 하나만 유지된다
        assertThat(chatRoomRepository.findByUser(escort.id)).hasSize(1)
        // 다시 들어온 쪽은 읽음 처리된다
        assertThat(partOf(escort).lastReadAt).isAfterOrEqualTo(before)
    }

    @Test
    @DisplayName("[ChatService] 상대를 바꿔 부르면 다른 채팅방이 만들어진다")
    fun createChatRoomWithAnotherPartner() {
        val another = chatService.createChatRoom(client.id, stranger.id)
        em.flush()

        assertThat(another.requiredId).isNotEqualTo(chatRoom.requiredId)
        assertThat(chatRoomRepository.findByUser(client.id)).hasSize(2)
    }

    // ===== 메시지 전송 =====

    @Test
    @DisplayName("[ChatService] 상대가 떠난 채팅방에 메시지를 보내면 상대를 다시 입장시킨다")
    fun sendMessageRejoinsPartnerWhoLeft() {
        chatService.leaveChatRoom(client.id, chatRoom.requiredId)
        em.flush()
        assertThat(partOf(client).isActive).isFalse()

        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        em.flush()

        // 떠난 상대가 다시 활성화되어 새 메시지를 받을 수 있다
        assertThat(partOf(client).isActive).isTrue()
        // 보낸 사람은 그대로 활성 상태다
        assertThat(partOf(escort).isActive).isTrue()
    }

    @Test
    @DisplayName("[ChatService] 입장 내역이 없으면 메시지를 보낼 수 없다")
    fun sendMessageWithoutChatPart() {
        assertThatThrownBy { chatService.sendMessage(chatRoom.requiredId, stranger.id, clientContent) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("채팅 입장 내역이 없습니다.")
    }

    @Test
    @DisplayName("[ChatService] 없는 채팅방에 메시지를 보내면 입장 내역이 없다고 막힌다")
    fun sendMessageToMissingChatRoom() {
        // 입장 내역을 먼저 찾기 때문에 '존재하지 않는 채팅방'(122)이 아니라 121 로 걸러진다
        assertThatThrownBy { chatService.sendMessage(10_000L, escort.id, escortContent) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("채팅 입장 내역이 없습니다.")
    }

    // ===== 메시지 조회 =====

    @Test
    @DisplayName("[ChatService] 메시지를 조회하면 읽은 시각이 갱신된다")
    fun getMessagesUpdatesLastReadAt() {
        val before = partOf(client).lastReadAt

        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        em.flush()
        messageContents(client)
        em.flush()

        assertThat(partOf(client).lastReadAt).isAfter(before)
    }

    @Test
    @DisplayName("[ChatService] 입장 내역이 없으면 메시지를 조회할 수 없다")
    fun getMessagesWithoutChatPart() {
        assertThatThrownBy { chatService.getMessages(stranger.id, chatRoom.requiredId, pageable()) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessage("채팅방에 접속하실 권한이 없습니다.")
    }

    @Test
    @DisplayName("[ChatService] 떠난 채팅방은 메시지를 조회할 수 없다")
    fun getMessagesAfterLeave() {
        chatService.leaveChatRoom(client.id, chatRoom.requiredId)
        em.flush()

        assertThatThrownBy { chatService.getMessages(client.id, chatRoom.requiredId, pageable()) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 떠난 채팅방입니다.")
    }

    // ===== 채팅방 퇴장 =====

    @Test
    @DisplayName("[ChatService] 채팅방을 떠나면 입장 내역이 비활성화되고 안내 메시지가 남는다")
    fun leaveChatRoomMarksPartInactive() {
        chatService.leaveChatRoom(client.id, chatRoom.requiredId)
        em.flush()

        assertThat(partOf(client).isActive).isFalse()
        // 상대는 영향을 받지 않는다
        assertThat(partOf(escort).isActive).isTrue()

        // 퇴장 안내가 마지막 채팅으로 남는다
        val leaveContent = client.name + leaveRoomMessage
        assertThat(chatRoomRepository.findByIdOrNull(chatRoom.requiredId)!!.lastChatContent)
            .isEqualTo(leaveContent)

        // 남아 있는 쪽은 퇴장 메시지를 LEAVE 타입으로 본다
        val messages = chatService.getMessages(escort.id, chatRoom.requiredId, pageable()).content
        assertThat(messages.first().content).isEqualTo(leaveContent)
        assertThat(messages.first().type).isEqualTo(MessageType.LEAVE)
        assertThat(messages.last().content).isEqualTo(escort.name + createRoomMessage)
    }

    @Test
    @DisplayName("[ChatService] 이미 떠난 채팅방은 다시 떠날 수 없다")
    fun leaveChatRoomTwice() {
        chatService.leaveChatRoom(client.id, chatRoom.requiredId)
        em.flush()

        assertThatThrownBy { chatService.leaveChatRoom(client.id, chatRoom.requiredId) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 떠난 채팅방입니다.")
    }

    @Test
    @DisplayName("[ChatService] 없는 채팅방은 떠날 수 없다")
    fun leaveMissingChatRoom() {
        assertThatThrownBy { chatService.leaveChatRoom(client.id, 10_000L) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("존재하지 않는 채팅방입니다.")
    }

    @Test
    @DisplayName("[ChatService] 입장 내역이 없으면 채팅방을 떠날 수 없다")
    fun leaveChatRoomWithoutChatPart() {
        assertThatThrownBy { chatService.leaveChatRoom(stranger.id, chatRoom.requiredId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("채팅 입장 내역이 없습니다.")
    }

    // ===== 안 읽은 메시지 수 =====

    @Test
    @DisplayName("[ChatService] 안 읽은 수는 읽은 시각 이후에 온 상대 메시지만 센다")
    fun unreadCountsOnlyPartnerMessagesAfterLastRead() {
        // 의뢰인이 한 번 읽으면 안 읽은 메시지가 없다
        messageContents(client)
        em.flush()
        assertThat(chatService.getUnReadCounts(client.id, listOf(chatRoom))[chatRoom.requiredId])
            .isEqualTo(0L)

        // 읽은 뒤 상대가 2건 보내면 2건이 쌓인다
        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        em.flush()
        assertThat(chatService.getUnReadCounts(client.id, listOf(chatRoom))[chatRoom.requiredId])
            .isEqualTo(2L)

        // 자기가 보낸 메시지는 자신의 안 읽은 수에 들어가지 않는다
        assertThat(chatService.getUnReadCounts(escort.id, listOf(chatRoom))[chatRoom.requiredId])
            .isEqualTo(0L)
    }

    @Test
    @DisplayName("[ChatService] 안 읽은 수는 채팅방별로 따로 센다")
    fun unreadCountsPerChatRoom() {
        val otherRoom = chatService.createChatRoom(client.id, stranger.id)
        messageContents(client)
        em.flush()

        chatService.sendMessage(chatRoom.requiredId, escort.id, escortContent)
        em.flush()

        val counts = chatService.getUnReadCounts(client.id, listOf(chatRoom, otherRoom))

        assertThat(counts[chatRoom.requiredId]).isEqualTo(1L)
        // 의뢰인이 직접 연 방의 개설 안내는 본인 메시지라 세지 않는다
        assertThat(counts[otherRoom.requiredId]).isEqualTo(0L)
    }

    @Test
    @DisplayName("[ChatService] 채팅방이 없으면 안 읽은 수는 빈 결과다")
    fun unreadCountsWithoutChatRooms() {
        assertThat(chatService.getUnReadCounts(client.id, emptyList())).isEmpty()
    }
}
