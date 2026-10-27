package com.back.nbe12142team06.domain.post.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.TossCancelResponse
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.dto.PostSearchConditionDto
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManagerFactory
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:post_controller_test;MODE=MySQL"])
@AutoConfigureMockMvc
@Transactional
class PostControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    @Autowired
    private lateinit var rideRepository: RideRepository

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private var testUserId: Long = 0
    private lateinit var accessTokenCookie: Cookie

    private fun login(username: String, password: String): Cookie {
        return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "$password"
                    }
                    """,
                ),
        )
            .andReturn()
            .response
            .getCookie("accessToken")!!
    }

    @BeforeEach
    fun setUp() {
        // 공고 목록 캐시는 테스트가 끝나도 남아서 다른 테스트의 조회에 섞이므로 매번 비운다
        cacheManager.getCache("postList")!!.clear()

        val username = "testUser"
        val password = "testPassword"

        val user = User(
            username,
            passwordEncoder.encode(password),
            "test@test.com",
            "테스트유저",
            Role.CLIENT,
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "010-1234-5678",
            "서울",
        )
        testUserId = userRepository.save(user).id

        // 로그인해 인증 쿠키 확보
        accessTokenCookie = login(username, password)
    }

    //공고등록
    private fun registerPost(): Long {
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)

        val response = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPostJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt)),
            )
            .andReturn().response.contentAsString

        val ctx = com.jayway.jsonpath.JsonPath.parse(response)
        return ctx.read<Number>("$.data.id").toLong()
    }

    //결제 승인 처리
    private fun approvePayment(postId: Long) {
        val payment = paymentRepository.findAll().first { it.post.id == postId }
        payment.statusUpdate(PaymentStatus.IN_PROGRESS)
        payment.statusUpdate(PaymentStatus.DONE)
        paymentRepository.saveAndFlush(payment)
    }

    // IN_PROGRESS 상태의 공고 만들기 (escortComplete 테스트용 사전 준비)
    private fun setUpInProgressPost(): Long {
        val postId = registerPost()
        val post = postRepository.findByIdOrNull(postId)!!
        post.match()
        post.startProgress(LocalDateTime.now().minusHours(1)) // 임시값, escortComplete가 실제값으로 덮어씀
        postRepository.saveAndFlush(post)
        return postId
    }

    // 해당 공고에 매칭된(ACCEPTED) 지원 만들기
    private fun setUpAcceptedApplication(postId: Long, suffix: String): Long {
        val escort = User(
            "escortUser$suffix", passwordEncoder.encode("escortPassword"),
            "escort$suffix@test.com", "동행인", Role.ESCORT, Gender.MALE,
            LocalDate.of(1990, 1, 1), "010-5555-$suffix", "인천",
        )
        userRepository.save(escort)

        // 동행 매니저 프로필 (매칭된 동행 매니저는 반드시 프로필이 있음)
        escortProfileRepository.save(
            EscortProfile(
                escort,
                "동행 매니저입니다.",
                "오픈은행",
                escort.name,
                "123-0000000-000",
            ),
        )

        val application = Application(postRepository.findByIdOrNull(postId)!!, escort)
        application.accept()
        return applicationRepository.save(application).requiredId
    }

    @Test
    @DisplayName("[PostController] 공고 목록 조회 - 정상 조회")
    fun t1() {
        val postId = registerPost()
        approvePayment(postId)   // 결제완료 처리해야 목록에 노출됨

        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-11"))
            .andExpect(jsonPath("$.msg").value("목록 조회 성공"))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(1))
    }

    @Test
    @DisplayName("[PostController] 공고 목록 조회 - 데이터 없을 때 빈 배열 반환")
    fun t2() {
        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
    }

    @Test
    @DisplayName("[PostController] 공고 상세 조회 - 정상 조회")
    fun t3() {
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)

        // 먼저 공고 등록
        val writeResult = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPostJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt)),
            )

        val ctx = com.jayway.jsonpath.JsonPath.parse(
            writeResult.andReturn().response.contentAsString,
        )
        val postId = ctx.read<Number>("$.data.id").toLong()

        // 등록된 공고 조회
        val resultActions = mvc
            .perform(
                get("/api/v1/posts/{id}", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("detail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-15"))
            .andExpect(jsonPath("$.msg").value("상세 조회 성공"))
            .andExpect(jsonPath("$.data.id").value(postId))
            .andExpect(jsonPath("$.data.title").exists())
            .andExpect(jsonPath("$.data.postStatus").exists())
    }

    @Test
    @DisplayName("[PostController] 공고 상세 조회 - 존재하지 않는 id 조회 시 404 반환")
    fun t4() {
        val notExistingId = 999L

        val resultActions = mvc
            .perform(
                get("/api/v1/posts/{id}", notExistingId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("detail"))
            .andExpect(status().isNotFound())
    }

    // 시간 순서: 모집시작 -> 모집마감 -> 동행시작 -> 동행종료
    private fun validPostJson(
        recruitStartAt: LocalDateTime,
        recruitEndAt: LocalDateTime,
        escortStartAt: LocalDateTime,
        escortEndAt: LocalDateTime,
        toHospital: RideSelect = RideSelect.TAXI,
        toHome: RideSelect = RideSelect.TAXI,
    ): String {
        return """
            {
                "title": "정형외과 동행 구합니다",
                "content": "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
                "region": "서울",
                "hospitalName": "서울성모병원",
                "hospitalAddress": "서울 서초구 반포대로 222",
                "hospitalLat": 37.5012743,
                "hospitalLng": 127.0051893,
                "pickupAddress": "서울 서초구 잠원동 10-1",
                "pickupLat": 37.5160000,
                "pickupLng": 127.0200000,
                "hourlyPay": 15000,
                "recruitStartAt": "$recruitStartAt",
                "recruitEndAt": "$recruitEndAt",
                "escortStartAt": "$escortStartAt",
                "escortEndAt": "$escortEndAt",
                "rideSelectToHospital": "$toHospital",
                "rideSelectToHome": "$toHome",
                "patientNote": "거동이 불편하신 70대 어르신",
                "reportRequired": true
            }
            """
    }

    private fun selectedOf(postId: Long, direction: RideDirection): RideSelect? {
        return rideRepository.findAllByPostId(postId)
            .first { it.direction == direction }
            .selected
    }

    @Test
    @DisplayName("[PostController] 공고 등록 - 정상 등록")
    fun t5() {
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPostJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt)),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("write"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-11"))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.postStatus").value("모집 중"))
    }

    @Test
    @DisplayName("[PostController] 공고 등록 - 필수값 누락 시 400-01 반환")
    fun t6() {
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "title": "",
                            "content": "",
                            "region": "",
                            "hospitalName": "서울성모병원",
                            "hospitalAddress": "서울 서초구 반포대로 222",
                            "hospitalLat": 37.5012743,
                            "hospitalLng": 127.0051893,
                            "pickupAddress": "서울 서초구 잠원동 10-1",
                            "pickupLat": 37.5160000,
                            "pickupLng": 127.0200000,
                            "hourlyPay": 15000,
                            "recruitStartAt": "$recruitStartAt",
                            "recruitEndAt": "$recruitEndAt",
                            "escortStartAt": "$escortStartAt",
                            "escortEndAt": "$escortEndAt",
                            "reportRequired": true
                        }
                        """,
                    ),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("write"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
    }

    @Test
    @DisplayName("[PostController] 공고 등록 - 시급 0 이하 시 400-01 반환")
    fun t7() {
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "title": "정형외과 동행 구합니다",
                            "content": "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
                            "region": "서울",
                            "hospitalName": "서울성모병원",
                            "hospitalAddress": "서울 서초구 반포대로 222",
                            "hospitalLat": 37.5012743,
                            "hospitalLng": 127.0051893,
                            "pickupAddress": "서울 서초구 잠원동 10-1",
                            "pickupLat": 37.5160000,
                            "pickupLng": 127.0200000,
                            "hourlyPay": 0,
                            "recruitStartAt": "$recruitStartAt",
                            "recruitEndAt": "$recruitEndAt",
                            "escortStartAt": "$escortStartAt",
                            "escortEndAt": "$escortEndAt",
                            "reportRequired": true
                        }
                        """,
                    ),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("write"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
    }

    @Test
    @DisplayName("[PostController] 공고 등록 - 동행 시작 시간이 종료 시간보다 늦을 때 400-14 반환")
    fun t8() {
        // escortStartAt > escortEndAt만 위반하고, 나머지는 현재 시각 기준 상대값으로 설정
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = now.plusDays(2)
        val escortEndAt = now.plusDays(3)
        val escortStartAt = escortEndAt.plusHours(3) // 일부러 종료 시간보다 늦게 설정

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPostJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt)),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("write"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-14"))
            .andExpect(jsonPath("$.msg").value("동행 시작 시간은 종료 시간보다 빨라야 합니다."))
    }

    @Test
    @DisplayName("[PostController] 공고 등록 - 마감 시간이 동행 시작 시간보다 늦을 때 400-13 반환")
    fun t9() {
        val now = LocalDateTime.now()
        val escortStartAt = now.plusDays(3)
        val escortEndAt = escortStartAt.plusHours(3)
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = escortStartAt.plusDays(1) // 일부러 escortStartAt보다 늦게 설정

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPostJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt)),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("write"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-13"))
            .andExpect(jsonPath("$.msg").value("모집 마감 시간은 동행 시작 시간보다 빨라야 합니다."))
    }

    @Test
    @DisplayName("[PostController] 공고 삭제 - 의뢰인 본인이 작성한 공고가 아닌 경우")
    fun t10() {
        // testUser로 공고 등록
        val postId = registerPost()

        // 다른 유저 생성 후 로그인
        val otherUsername = "otherUser"
        val otherPassword = "otherPassword"
        val otherUser = User(
            otherUsername,
            passwordEncoder.encode(otherPassword),
            "other@test.com",
            "다른유저",
            Role.CLIENT,
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "010-9999-9999",
            "부산",
        )
        userRepository.save(otherUser)
        val otherAccessTokenCookie = login(otherUsername, otherPassword)

        // 다른 유저로 삭제 시도
        val resultActions = mvc
            .perform(
                delete("/api/v1/posts/{id}", postId)
                    .cookie(otherAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("delete"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-15"))
            .andExpect(jsonPath("$.msg").value("본인이 작성한 공고만 삭제할 수 있습니다."))
    }

    @Test
    @DisplayName("[PostController] 공고 취소 - 관리자는 소유자가 아니어도 매칭된 공고 취소 가능")
    fun t11() {
        // testUser로 공고 등록
        val postId = registerPost()

        val post = postRepository.findByIdOrNull(postId)!!
        post.match()
        postRepository.saveAndFlush(post)

        // 관리자 계정 생성 후 로그인
        val adminUsername = "adminUser"
        val adminPassword = "adminPassword"
        val admin = User(
            adminUsername,
            passwordEncoder.encode(adminPassword),
            "admin@test.com",
            "관리자",
            Role.ADMIN,
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            "010-0000-0000",
            "서울",
        )
        userRepository.save(admin)
        val adminAccessTokenCookie = login(adminUsername, adminPassword)

        // 소유자가 아닌 관리자 계정으로 매칭 취소 시도 -> 성공해야 함
        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/matchedCancel", postId)
                    .cookie(adminAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("matchedCancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-17"))
    }

    @Test
    @DisplayName("[PostController] 공고 취소 - 의뢰인 본인이 매칭된 공고 취소 성공")
    fun t12() {
        val postId = registerPost()
        val post = postRepository.findByIdOrNull(postId)!!
        post.match()
        postRepository.saveAndFlush(post)

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/matchedCancel", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("matchedCancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-17"))
    }

    @Test
    @DisplayName("[PostController] 공고 취소 - 매칭 안 된 상태에서 취소 시도 시 400 반환")
    fun t13() {
        val postId = registerPost() // OPEN 상태 그대로

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/matchedCancel", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("matchedCancel"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-18"))
    }

    @Test
    @DisplayName("[PostController] 공고 취소 - 본인이 작성한 공고가 아닌 경우 403 반환")
    fun t14() {
        val postId = registerPost()
        val post = postRepository.findByIdOrNull(postId)!!
        post.match()
        postRepository.saveAndFlush(post)

        val otherUsername = "otherUser2"
        val otherPassword = "otherPassword2"
        val otherUser = User(
            otherUsername, passwordEncoder.encode(otherPassword),
            "other2@test.com", "다른유저2", Role.CLIENT, Gender.MALE,
            LocalDate.of(1990, 1, 1), "010-1111-2222", "부산",
        )
        userRepository.save(otherUser)
        val otherAccessTokenCookie = login(otherUsername, otherPassword)

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/matchedCancel", postId)
                    .cookie(otherAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-17"))
    }

    @Test
    @DisplayName("[PostController] 공고 취소 - 동행인(ESCORT) 역할은 취소 권한 없음 403 반환")
    fun t15() {
        val postId = registerPost()
        val post = postRepository.findByIdOrNull(postId)!!
        post.match()
        postRepository.saveAndFlush(post)

        val escortUsername = "escortUser"
        val escortPassword = "escortPassword"
        val escortUser = User(
            escortUsername, passwordEncoder.encode(escortPassword),
            "escort@test.com", "동행인", Role.ESCORT, Gender.MALE,
            LocalDate.of(1990, 1, 1), "010-3333-4444", "인천",
        )
        userRepository.save(escortUser)
        val escortAccessTokenCookie = login(escortUsername, escortPassword)

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/matchedCancel", postId)
                    .cookie(escortAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[PostController] 공고 목록 조회 - 결제 취소된 공고는 제외")
    fun t16() {
        val postId = registerPost()

        val payment = paymentRepository.findAll().first { it.post.id == postId }
        payment.statusUpdate(PaymentStatus.DELETED)
        payment.cancelPayment("테스트 취소")
        paymentRepository.saveAndFlush(payment)

        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(0))
    }

    @Test
    @DisplayName("[PostController] 공고 목록 조회 - 취소 후 재결제되면 다시 노출")
    fun t17() {
        val postId = registerPost()

        val payment = paymentRepository.findAll().first { it.post.id == postId }
        payment.statusUpdate(PaymentStatus.DELETED)
        val repayment = payment.cancelPayment("테스트 취소") // 기존 건 취소 + 재결제용 새 객체 반환

        repayment.statusUpdate(PaymentStatus.IN_PROGRESS)
        repayment.statusUpdate(PaymentStatus.DONE)   // 재결제 완료 처리 추가

        paymentRepository.saveAndFlush(payment)   // 취소 처리 저장
        paymentRepository.save(repayment)         // 재결제 건 저장

        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(postId))
    }

    @Test
    @DisplayName("[PostService] 만료 배치 - 모집마감 지난 OPEN 공고는 EXPIRED로 전환된다")
    fun t18() {
        val client = userRepository.findByIdOrNull(testUserId)!!
        val now = LocalDateTime.now()

        // API로는 과거 날짜 공고를 못 만드니 리포지토리로 직접 생성
        val expiredTargetPost = Post(
            client = client,
            title = "만료 테스트 공고",
            content = "만료 배치 테스트용",
            region = "서울",
            hospitalName = "테스트병원",
            hospitalAddress = "테스트주소",
            hospitalLat = BigDecimal.valueOf(37.5),
            hospitalLng = BigDecimal.valueOf(127.0),
            pickupAddress = "테스트픽업주소",
            pickupLat = BigDecimal.valueOf(37.5),
            pickupLng = BigDecimal.valueOf(127.0),
            hourlyPay = 15000,
            recruitStartAt = now.minusDays(3),
            recruitEndAt = now.minusDays(1),     // 이미 지난 마감시간
            escortStartAt = now.plusDays(1),
            escortEndAt = now.plusDays(1).plusHours(3),
        )
        val targetPostId = postRepository.save(expiredTargetPost).requiredId

        // when
        postService.expireOverduePosts()

        // then
        val result = postRepository.findByIdOrNull(targetPostId)!!
        assertThat(result.postStatus).isEqualTo(PostStatus.EXPIRED)
    }

    @Test
    @DisplayName("[PostService] 만료 배치 - 매칭된 공고는 마감시간 지나도 대상에서 제외된다")
    fun t19() {
        val client = userRepository.findByIdOrNull(testUserId)!!
        val now = LocalDateTime.now()
        //registerPost() 대신 직접 만들어야 시간 체크 피해감
        val matchedPost = Post(
            client = client,
            title = "매칭된 공고",
            content = "만료 배치 제외 테스트용",
            region = "서울",
            hospitalName = "테스트병원",
            hospitalAddress = "테스트주소",
            hospitalLat = BigDecimal.valueOf(37.5),
            hospitalLng = BigDecimal.valueOf(127.0),
            pickupAddress = "테스트픽업주소",
            pickupLat = BigDecimal.valueOf(37.5),
            pickupLng = BigDecimal.valueOf(127.0),
            hourlyPay = 15000,
            recruitStartAt = now.minusDays(3),
            recruitEndAt = now.minusDays(1),     // 마감 지남
            escortStartAt = now.plusDays(1),
            escortEndAt = now.plusDays(1).plusHours(3),
        )
        matchedPost.match()     // 이미 매칭된 상태로 직접 세팅
        val matchedPostId = postRepository.save(matchedPost).requiredId

        // when
        postService.expireOverduePosts()

        // then
        val result = postRepository.findByIdOrNull(matchedPostId)!!
        assertThat(result.postStatus).isEqualTo(PostStatus.MATCHED) // 그대로 유지돼야 함
    }

    @Test
    @DisplayName("[PostController] 공고 목록 조회 - 결제 완료 전(READY) 공고는 제외")
    fun t20() {
        registerPost() // 결제 상태 건드리지 않음 -> 기본값 READY

        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(0))
    }

    @Test
    @DisplayName("[PostController] 검색 - 키워드로 조회")
    fun t21() {
        val postId = registerPost()
        approvePayment(postId)

        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
                    .param("keyword", "정형외과")
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(postId))
    }

    // t22() 테스트에서 토스 API를 목으로 교체하기 위해 추가했습니다.
    @MockitoBean
    private lateinit var tossPaymentClient: TossPaymentClient

    @Test
    @DisplayName("[PostController] 동행완료 처리 - 정상 처리")
    fun t22() {
        val postId = setUpInProgressPost()
        val applicationId = setUpAcceptedApplication(postId, "25")

        val departedAt = LocalDateTime.now().minusHours(1)
        val arrivedAt = LocalDateTime.now()

        escortProgressLogRepository.save(
            EscortProgressLog(
                application = applicationRepository.findByIdOrNull(applicationId)!!,
                progress = EscortProgress.DEPARTED,
                occurredAt = departedAt,
            ),
        )
        escortProgressLogRepository.save(
            EscortProgressLog(
                application = applicationRepository.findByIdOrNull(applicationId)!!,
                progress = EscortProgress.ARRIVED_HOME,
                occurredAt = arrivedAt,
            ),
        )

        // 실제 토스 API 이용하지 않도록 아래를 추가했습니다.
        given(tossPaymentClient.callApiCancel(any(), any(), any()))
            .willReturn(ResponseEntity.ok(TossCancelResponse("계좌이체", "30000", "0", "tempPaymentKey", "tempOrderId")))

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(PostController::class.java))
            .andExpect(handler().methodName("escortComplete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-18"))

        val result = postRepository.findByIdOrNull(postId)!!
        assertThat(result.postStatus).isEqualTo(PostStatus.COMPLETED)
        assertThat(result.escortStartAt.hour).isEqualTo(departedAt.hour)
        assertThat(result.escortStartAt.minute).isEqualTo(departedAt.minute)
        assertThat(result.escortEndAt.hour).isEqualTo(arrivedAt.hour)
        assertThat(result.escortEndAt.minute).isEqualTo(arrivedAt.minute)

        val escortId = applicationRepository.findByIdOrNull(applicationId)!!.escort.id
        val escortProfile = escortProfileRepository.findByIdOrNull(escortId)!!
        assertThat(escortProfile.completedCount).isEqualTo(1)
    }

    @Test
    @DisplayName("[PostController] 동행완료 처리 - 출발 기록 없으면 404")
    fun t23() {
        val postId = setUpInProgressPost()
        setUpAcceptedApplication(postId, "26")
        // DEPARTED, ARRIVED_HOME 로그 둘 다 안 만듦

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-22"))
            .andExpect(jsonPath("$.msg").value("동행 출발 기록이 없습니다."))
    }

    @Test
    @DisplayName("[PostController] 동행완료 처리 - 귀가완료 기록 없으면 404")
    fun t24() {
        val postId = setUpInProgressPost()
        val applicationId = setUpAcceptedApplication(postId, "27")

        escortProgressLogRepository.save(
            EscortProgressLog(
                application = applicationRepository.findByIdOrNull(applicationId)!!,
                progress = EscortProgress.DEPARTED,
                occurredAt = LocalDateTime.now().minusHours(1),
            ),
        )
        // ARRIVED_HOME 로그는 안 만듦

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-23"))
            .andExpect(jsonPath("$.msg").value("귀가완료 기록이 없습니다."))
    }

    @Test
    @DisplayName("[PostController] 동행완료 처리 - IN_PROGRESS 상태 아니면 400")
    fun t25() {
        val postId = registerPost() // OPEN 상태 그대로

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(accessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-19"))
            .andExpect(jsonPath("$.msg").value("동행진행중 상태에서만 동행완료 처리할 수 있습니다."))
    }

    @Test
    @DisplayName("[PostController] 동행완료 처리 - 본인이 작성한 공고가 아닌 경우 403")
    fun t26() {
        val postId = setUpInProgressPost()

        val otherUsername = "otherUser29"
        val otherPassword = "otherPassword29"
        val otherUser = User(
            otherUsername, passwordEncoder.encode(otherPassword),
            "other29@test.com", "다른유저", Role.CLIENT, Gender.MALE,
            LocalDate.of(1990, 1, 1), "010-7777-8888", "부산",
        )
        userRepository.save(otherUser)
        val otherAccessTokenCookie = login(otherUsername, otherPassword)

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(otherAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-19"))
    }

    @Test
    @DisplayName("[PostController] 동행완료 처리 - 동행인(ESCORT) 역할은 권한 없음 403")
    fun t27() {
        val postId = setUpInProgressPost()

        val escortUsername = "escortUser30"
        val escortPassword = "escortPassword30"
        val escortUser = User(
            escortUsername, passwordEncoder.encode(escortPassword),
            "escort30@test.com", "동행인", Role.ESCORT, Gender.MALE,
            LocalDate.of(1990, 1, 1), "010-9999-0000", "인천",
        )
        userRepository.save(escortUser)
        val escortAccessTokenCookie = login(escortUsername, escortPassword)

        val resultActions = mvc
            .perform(
                patch("/api/v1/posts/{postId}/escortComplete", postId)
                    .cookie(escortAccessTokenCookie),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[PostController] 공고 수정 - 이동수단도 함께 변경된다")
    fun t28() {
        val postId = registerPost() // 등록 시 갈 때·올 때 모두 TAXI
        assertThat(selectedOf(postId, RideDirection.TO_HOSPITAL)).isEqualTo(RideSelect.TAXI)
        assertThat(selectedOf(postId, RideDirection.TO_HOME)).isEqualTo(RideSelect.TAXI)

        val now = LocalDateTime.now()
        val resultActions = mvc
            .perform(
                put("/api/v1/posts/{postId}", postId)
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validPostJson(
                            now.plusDays(1), now.plusDays(2),
                            now.plusDays(3), now.plusDays(3).plusHours(3),
                            RideSelect.WALK, RideSelect.BUS,
                        ),
                    ),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-16"))

        assertThat(selectedOf(postId, RideDirection.TO_HOSPITAL)).isEqualTo(RideSelect.WALK)
        assertThat(selectedOf(postId, RideDirection.TO_HOME)).isEqualTo(RideSelect.BUS)
    }

    @Test
    @DisplayName("[PostController] 공고 수정 - 이미 이동 중이면 400 반환")
    fun t29() {
        val postId = registerPost()

        // 갈 때 이동을 "이동 중"으로 바꿔 두면 이동수단을 더 이상 수정할 수 없어야 합니다.
        rideRepository.findAllByPostId(postId)
            .first { it.direction == RideDirection.TO_HOSPITAL }
            .updateStatus(RideStatus.IN_PROGRESS)

        val now = LocalDateTime.now()
        val resultActions = mvc
            .perform(
                put("/api/v1/posts/{postId}", postId)
                    .cookie(accessTokenCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validPostJson(
                            now.plusDays(1), now.plusDays(2),
                            now.plusDays(3), now.plusDays(3).plusHours(3),
                            RideSelect.WALK, RideSelect.BUS,
                        ),
                    ),
            )
            .andDo(print())

        resultActions
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-81"))
    }

    // ===== 공고 조회 권한 / 환자 특이사항 노출 =====

    private fun saveUser(username: String, email: String, role: Role): User {
        return userRepository.save(
            User(
                username, passwordEncoder.encode("pw1234!"),
                email, username, role, Gender.MALE,
                LocalDate.of(1990, 1, 1), "010-5555-6666", "서울",
            ),
        )
    }

    @Test
    @DisplayName("[PostController] 공고 조회 - 비로그인은 목록·상세 모두 401")
    fun sensitive1() {
        val postId = registerPost()

        mvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized())
        mvc.perform(get("/api/v1/posts/{postId}", postId)).andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("[PostController] 공고 상세 - 작성자는 환자 특이사항을 본다")
    fun sensitive2() {
        val postId = registerPost()

        mvc.perform(get("/api/v1/posts/{postId}", postId).cookie(accessTokenCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.patientNote").value("거동이 불편하신 70대 어르신"))
            .andExpect(jsonPath("$.data.canViewSensitive").value(true))
    }

    @Test
    @DisplayName("[PostController] 공고 상세 - 승인되지 않은 일반 회원은 환자 특이사항이 가려진다")
    fun sensitive3() {
        val postId = registerPost()
        saveUser("stranger", "stranger@test.com", Role.ESCORT)
        val cookie = login("stranger", "pw1234!")

        mvc.perform(get("/api/v1/posts/{postId}", postId).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").exists())
            .andExpect(jsonPath("$.data.patientNote").doesNotExist())
            .andExpect(jsonPath("$.data.canViewSensitive").value(false))
    }

    @Test
    @DisplayName("[PostController] 공고 상세 - 대기 중인 지원자는 가려지고, 승인된 동행인은 본다")
    fun sensitive4() {
        val postId = registerPost()
        val post = postRepository.findByIdOrNull(postId)!!
        val escort = saveUser("escortA", "escortA@test.com", Role.ESCORT)
        val application = applicationRepository.saveAndFlush(
            Application(post, escort),
        )
        val cookie = login("escortA", "pw1234!")

        // 대기(PENDING) 상태
        mvc.perform(get("/api/v1/posts/{postId}", postId).cookie(cookie))
            .andExpect(jsonPath("$.data.patientNote").doesNotExist())
            .andExpect(jsonPath("$.data.canViewSensitive").value(false))

        // 승인(ACCEPTED) 후
        application.accept()
        applicationRepository.saveAndFlush(application)

        mvc.perform(get("/api/v1/posts/{postId}", postId).cookie(cookie))
            .andExpect(jsonPath("$.data.patientNote").value("거동이 불편하신 70대 어르신"))
            .andExpect(jsonPath("$.data.canViewSensitive").value(true))
    }

    @Test
    @DisplayName("[PostController] 공고 상세 - 관리자는 환자 특이사항을 본다")
    fun sensitive5() {
        val postId = registerPost()
        saveUser("adminUser2", "admin2@test.com", Role.ADMIN)
        val cookie = login("adminUser2", "pw1234!")

        mvc.perform(get("/api/v1/posts/{postId}", postId).cookie(cookie))
            .andExpect(jsonPath("$.data.patientNote").value("거동이 불편하신 70대 어르신"))
            .andExpect(jsonPath("$.data.canViewSensitive").value(true))
    }

    @Test
    @DisplayName("[PostController] 공고 목록 - 작성자가 봐도 환자 특이사항은 내려가지 않는다")
    fun sensitive6() {
        val postId = registerPost()
        // 결제 완료된 공고만 목록에 나온다
        val payment = paymentRepository.findAll().first { it.post.id == postId }
        payment.statusUpdate(PaymentStatus.IN_PROGRESS)
        payment.statusUpdate(PaymentStatus.DONE)
        paymentRepository.saveAndFlush(payment)

        mvc.perform(get("/api/v1/posts").cookie(accessTokenCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].title").exists())
            .andExpect(jsonPath("$.data.content[0].patientNote").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].canViewSensitive").value(false))
    }

    // ===== 공고 목록 캐시 =====

    private val openCondition = PostSearchConditionDto(null, null, null, null, null, null, true)

    // 결제가 완료된 공고만 목록에 나오므로 결제를 완료 상태로 만든다
    private fun registerPaidPost(): Long {
        val postId = registerPost()
        val payment = paymentRepository.findAll().first { it.post.id == postId }
        payment.statusUpdate(PaymentStatus.IN_PROGRESS)   // 결제 대기 → 결제 중 → 결제 완료 순서를 지킨다
        payment.statusUpdate(PaymentStatus.DONE)
        paymentRepository.saveAndFlush(payment)
        return postId
    }

    @Test
    @DisplayName("[PostList 캐시] 같은 조건으로 두 번 조회하면 두 번째는 DB를 거치지 않는다")
    fun cache1() {
        registerPaidPost()
        val stats = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        stats.setStatisticsEnabled(true)

        val first = postService.searchDtos(openCondition, PageRequest.of(0, 8))
        stats.clear()
        val second = postService.searchDtos(openCondition, PageRequest.of(0, 8))

        assertThat(first.totalElements).isEqualTo(1L)
        assertThat(second).isSameAs(first)                       // 캐시에서 그대로 돌려줌
        assertThat(stats.prepareStatementCount).isZero()         // 두 번째 조회는 SQL이 나가지 않음
    }

    @Test
    @DisplayName("[PostList 캐시] 조건이 다르면 캐시를 따로 쓴다")
    fun cache2() {
        registerPaidPost()
        val open = postService.searchDtos(openCondition, PageRequest.of(0, 8))
        val closed = postService.searchDtos(
            PostSearchConditionDto(null, null, null, null, null, null, false), PageRequest.of(0, 8),
        )

        assertThat(open.totalElements).isEqualTo(1L)    // 모집 중 탭
        assertThat(closed.totalElements).isZero()       // 모집 종료 탭에는 없다
    }

    @Test
    @DisplayName("[PostList 캐시] 공고를 취소하면 캐시가 비워져 다음 조회에 바로 반영된다")
    fun cache3() {
        val postId = registerPaidPost()

        val before = postService.searchDtos(openCondition, PageRequest.of(0, 8))
        assertThat(before.totalElements).isEqualTo(1L)       // 이 결과가 캐시에 저장됨

        postService.cancel(postId, testUserId)

        val after = postService.searchDtos(openCondition, PageRequest.of(0, 8))
        assertThat(after.totalElements).isZero()             // 캐시가 비워지지 않았다면 1이 남는다
    }

    @Test
    @DisplayName("[PostList 캐시] 캐시된 목록에도 환자 특이사항은 들어 있지 않다")
    fun cache4() {
        registerPaidPost()

        val page = postService.searchDtos(openCondition, PageRequest.of(0, 8))

        assertThat(page.content[0].patientNote).isNull()
        assertThat(page.content[0].canViewSensitive).isFalse()
    }
}
