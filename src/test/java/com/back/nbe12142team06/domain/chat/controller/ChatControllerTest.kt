package com.back.nbe12142team06.domain.chat.controller

import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.chat.service.ChatService
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.service.UserService
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    private val createRoomMessage = "님이 채팅방을 개설했습니다."
    private val escortContent = "안녕하세요 저는 동행매니저 입니다."

    @field:Autowired private lateinit var mvc: MockMvc
    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var chatService: ChatService
    @field:Autowired private lateinit var chatPartRepository: ChatPartRepository
    @field:Autowired private lateinit var em: EntityManager

    // 실시간 전송은 외부(STOMP)로 나가므로 막아 둔다
    @field:MockitoBean private lateinit var messagingTemplate: SimpMessagingTemplate

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var stranger: User

    private lateinit var clientCookie: Cookie
    private lateinit var strangerCookie: Cookie

    @BeforeEach
    fun init() {
        client = signUp("chatApiClient", "의뢰인", Role.CLIENT, "010-4444-0001")
        escort = signUp("chatApiEscort", "동행매니저", Role.ESCORT, "010-4444-0002")
        stranger = signUp("chatApiStranger", "제3자", Role.CLIENT, "010-4444-0003")

        clientCookie = login("chatApiClient")
        strangerCookie = login("chatApiStranger")
    }

    private fun signUp(username: String, name: String, role: Role, phone: String): User =
        userService.signUp(
            UserSignUpRequest(
                username, "testPassword", "$username@test.test", name, role,
                Gender.MALE, LocalDate.of(1990, 5, 20), phone, "서울시"
            )
        )

    private fun login(username: String): Cookie =
        mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "testPassword"
                    }
                    """.trimIndent()
                )
        )
            .andReturn()
            .response
            .getCookie("accessToken")!!

    // 의뢰인 - 동행 매니저 채팅방을 미리 만들어 둔다 (개설 안내 메시지 1건 포함)
    private fun openChatRoom(): Long {
        val chatRoomId = chatService.createChatRoom(client.id, escort.id).requiredId
        em.flush()
        return chatRoomId
    }

    @Test
    @DisplayName("[ChatController] 채팅방 생성 - 성공")
    fun createChatRoom() {
        val resultActions = mvc.perform(
            post("/api/v1/chat")
                .param("user-id", escort.id.toString())
                .cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("createChatRoom"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-121"))
            .andExpect(jsonPath("$.msg").value("채팅방이 생성되었습니다."))
            // 응답에는 상대방 정보가 담긴다
            .andExpect(jsonPath("$.data.partnerId").value(escort.id))
            .andExpect(jsonPath("$.data.partnerName").value("동행매니저"))
            .andExpect(jsonPath("$.data.partnerRole").value("ESCORT"))
            .andExpect(jsonPath("$.data.lastChatContent").value("의뢰인$createRoomMessage"))
            .andExpect(jsonPath("$.data.unReadCount").value(0))
    }

    @Test
    @DisplayName("[ChatController] 채팅방 목록 조회 - 안 읽은 수 포함")
    fun list() {
        val chatRoomId = openChatRoom()
        chatService.sendMessage(chatRoomId, escort.id, escortContent)
        em.flush()

        val resultActions = mvc.perform(
            get("/api/v1/chat").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-121"))
            .andExpect(jsonPath("$.msg").value("채팅방 목록을 조회했습니다."))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].chatRoomId").value(chatRoomId))
            .andExpect(jsonPath("$.data[0].partnerId").value(escort.id))
            .andExpect(jsonPath("$.data[0].lastChatContent").value(escortContent))
            // 상대가 보낸 메시지 1건이 안 읽은 수로 잡힌다
            .andExpect(jsonPath("$.data[0].unReadCount").value(1))
    }

    @Test
    @DisplayName("[ChatController] 채팅 메시지 조회 - 최신순")
    fun getMessage() {
        val chatRoomId = openChatRoom()
        chatService.sendMessage(chatRoomId, escort.id, escortContent)
        em.flush()

        val resultActions = mvc.perform(
            get("/api/v1/chat/$chatRoomId").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("getMessage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-122"))
            .andExpect(jsonPath("$.msg").value("채팅 메시지를 조회했습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].content").value(escortContent))
            .andExpect(jsonPath("$.data.content[0].senderId").value(escort.id))
            .andExpect(jsonPath("$.data.content[0].type").value("TALK"))
            // 마지막은 채팅방 개설 안내 메시지
            .andExpect(jsonPath("$.data.content[1].content").value("의뢰인$createRoomMessage"))
            .andExpect(jsonPath("$.data.content[1].type").value("ENTER"))
    }

    @Test
    @DisplayName("[ChatController] 채팅 메시지 조회 - 참여하지 않은 채팅방이면 403")
    fun getMessageForbidden() {
        val chatRoomId = openChatRoom()

        val resultActions = mvc.perform(
            get("/api/v1/chat/$chatRoomId").cookie(strangerCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("getMessage"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-121"))
            .andExpect(jsonPath("$.msg").value("채팅방에 접속하실 권한이 없습니다."))
    }

    @Test
    @DisplayName("[ChatController] 채팅방 퇴장 - 성공")
    fun leaveChatRoom() {
        val chatRoomId = openChatRoom()

        val resultActions = mvc.perform(
            patch("/api/v1/chat/$chatRoomId").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("leaveChatRoom"))
            .andExpect(status().isNoContent())

        // 입장 내역이 비활성화된다
        assertThat(chatPartRepository.findByUserIdAndChatRoomId(client.id, chatRoomId)!!.isActive)
            .isFalse()
    }

    @Test
    @DisplayName("[ChatController] 채팅방 퇴장 - 없는 채팅방이면 404")
    fun leaveChatRoomNotFound() {
        val resultActions = mvc.perform(
            patch("/api/v1/chat/10000").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(ChatController::class.java))
            .andExpect(handler().methodName("leaveChatRoom"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-122"))
            .andExpect(jsonPath("$.msg").value("존재하지 않는 채팅방입니다."))
    }

    @Test
    @DisplayName("[ChatController] 로그인 없이 호출하면 401")
    fun chatApiRequiresLogin() {
        mvc.perform(get("/api/v1/chat"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))

        mvc.perform(post("/api/v1/chat").param("user-id", escort.id.toString()))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }
}
