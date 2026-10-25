package com.back.nbe12142team06.domain.notification.controller

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc
    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var notificationRepository: NotificationRepository
    @field:Autowired private lateinit var em: EntityManager

    private lateinit var client: User
    private lateinit var other: User
    private lateinit var clientCookie: Cookie

    @BeforeEach
    fun init() {
        client = signUp("noticeApiClient", "의뢰인", "010-6666-0001")
        other = signUp("noticeApiOther", "다른의뢰인", "010-6666-0002")
        clientCookie = login("noticeApiClient")
    }

    private fun signUp(username: String, name: String, phone: String): User =
        userService.signUp(
            UserSignUpRequest(
                username, "testPassword", "$username@test.test", name, Role.CLIENT,
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

    private fun saveNotification(receiver: User, content: String, url: String? = null): Notification =
        notificationRepository.save(Notification(content = content, url = url, receiver = receiver))

    @Test
    @DisplayName("[NotificationController] SSE 연결")
    fun connect() {
        val resultActions = mvc.perform(
            get("/api/v1/notification/connect").cookie(clientCookie)
        )

        resultActions
            .andExpect(handler().handlerType(NotificationController::class.java))
            .andExpect(handler().methodName("connect"))
            .andExpect(status().isOk())
            // SseEmitter 를 돌려주므로 응답이 비동기로 열린 채 유지된다
            .andExpect(request().asyncStarted())
    }

    @Test
    @DisplayName("[NotificationController] 알림 목록 조회")
    fun getPage() {
        val notification = saveNotification(client, "알림1", "/client/posts/1")
        saveNotification(other, "남의 알림")
        em.flush()

        val resultActions = mvc.perform(
            get("/api/v1/notification").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(NotificationController::class.java))
            .andExpect(handler().methodName("getPage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-132"))
            .andExpect(jsonPath("$.msg").value("알림 목록을 조회했습니다."))
            // 남의 알림은 섞이지 않는다
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(notification.requiredId))
            .andExpect(jsonPath("$.data.content[0].content").value("알림1"))
            .andExpect(jsonPath("$.data.content[0].url").value("/client/posts/1"))
            .andExpect(jsonPath("$.data.content[0].userId").value(client.id))
    }

    @Test
    @DisplayName("[NotificationController] 모든 알림 읽음 처리")
    fun readList() {
        repeat(3) { saveNotification(client, "알림$it") }
        em.flush()

        val resultActions = mvc.perform(
            patch("/api/v1/notification/read").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(NotificationController::class.java))
            .andExpect(handler().methodName("readList"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-133"))
            .andExpect(jsonPath("$.msg").value("알림을 읽음 처리했습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(3))

        em.flush()
        em.clear()
        assertThat(notificationRepository.findAll())
            .filteredOn { it.receiver.id == client.id }
            .allSatisfy { assertThat(it.isRead).isTrue() }
    }

    @Test
    @DisplayName("[NotificationController] 알림 읽음 처리 - 단건")
    fun read() {
        val notification = saveNotification(client, "알림1")
        em.flush()

        val resultActions = mvc.perform(
            patch("/api/v1/notification/${notification.requiredId}/read").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(NotificationController::class.java))
            .andExpect(handler().methodName("read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-133"))
            .andExpect(jsonPath("$.data.id").value(notification.requiredId))

        em.flush()
        em.clear()
        assertThat(notificationRepository.findById(notification.requiredId).get().isRead).isTrue()
    }

    @Test
    @DisplayName("[NotificationController] 알림 읽음 처리 - 남의 알림이면 403")
    fun readForbidden() {
        val notification = saveNotification(other, "남의 알림")
        em.flush()

        val resultActions = mvc.perform(
            patch("/api/v1/notification/${notification.requiredId}/read").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("read"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-131"))
            .andExpect(jsonPath("$.msg").value("알림을 읽을 권한이 없습니다."))
    }

    @Test
    @DisplayName("[NotificationController] 알림 읽음 처리 - 없는 알림이면 404")
    fun readNotFound() {
        val resultActions = mvc.perform(
            patch("/api/v1/notification/10000/read").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("read"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-131"))
            .andExpect(jsonPath("$.msg").value("알림을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[NotificationController] 알림 삭제")
    fun removeNotification() {
        val notification = saveNotification(client, "알림1")
        em.flush()

        val resultActions = mvc.perform(
            delete("/api/v1/notification/${notification.requiredId}").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(NotificationController::class.java))
            .andExpect(handler().methodName("removeNotification"))
            .andExpect(status().isNoContent())

        // 소프트 딜리트되어 목록에서 빠진다
        em.flush()
        em.clear()
        assertThat(notificationRepository.findById(notification.requiredId).get().deletedAt).isNotNull()
        mvc.perform(get("/api/v1/notification").cookie(clientCookie))
            .andExpect(jsonPath("$.data.content.length()").value(0))
    }

    @Test
    @DisplayName("[NotificationController] 알림 삭제 - 남의 알림이면 403")
    fun removeForbidden() {
        val notification = saveNotification(other, "남의 알림")
        em.flush()

        val resultActions = mvc.perform(
            delete("/api/v1/notification/${notification.requiredId}").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("removeNotification"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-132"))
            .andExpect(jsonPath("$.msg").value("알림을 삭제할 권한이 없습니다."))
    }

    @Test
    @DisplayName("[NotificationController] 알림 삭제 - 없는 알림이면 404")
    fun removeNotFound() {
        val resultActions = mvc.perform(
            delete("/api/v1/notification/10000").cookie(clientCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("removeNotification"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-131"))
            .andExpect(jsonPath("$.msg").value("알림을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[NotificationController] 로그인 없이 호출하면 401")
    fun notificationApiRequiresLogin() {
        mvc.perform(get("/api/v1/notification"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))

        mvc.perform(patch("/api/v1/notification/read"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))

        mvc.perform(delete("/api/v1/notification/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }
}
