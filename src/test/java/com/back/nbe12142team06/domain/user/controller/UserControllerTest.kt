package com.back.nbe12142team06.domain.user.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository
import com.back.nbe12142team06.global.security.JwtProvider
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Value("\${custom.jwt.secret-key}")
    private lateinit var secretKey: String

    // JSON 본문 요청
    private fun MockHttpServletRequestBuilder.json(body: String) =
        contentType(MediaType.APPLICATION_JSON).content(body)

    // 회원가입 요청 본문 (테스트마다 다른 값만 넘긴다)
    private fun signUpBody(
        username: String = "user1",
        password: String = "pwd1",
        email: String = "first@test.test",
        name: String = "김춘식",
        role: String = "CLIENT",
        gender: String = "MALE",
        birthDate: String = "1990-05-20",
        phoneNum: String = "010-1234-5678",
        region: String = "서울시",
    ) = """
        {
            "username": "$username",
            "password": "$password",
            "email": "$email",
            "name": "$name",
            "role": "$role",
            "gender": "$gender",
            "birthDate": "$birthDate",
            "phoneNum": "$phoneNum",
            "region": "$region"
        }
        """

    // 내 정보 수정 요청 본문 (가입 기본값과 같은 값이 기본)
    private fun updateBody(
        password: String = "pwd1",
        email: String = "first@test.test",
        name: String = "김춘식",
        birthDate: String = "1990-05-20",
        phoneNum: String = "010-1234-5678",
        region: String = "서울시",
    ) = """
        {
            "password": "$password",
            "email": "$email",
            "name": "$name",
            "birthDate": "$birthDate",
            "phoneNum": "$phoneNum",
            "region": "$region"
        }
        """

    // 의뢰인 프로필 요청 본문 (careNote 가 null 이면 JSON null)
    private fun clientProfileBody(
        emergencyContactName: String = "김철수",
        emergencyContactPhone: String = "010-1234-5678",
        careNote: String?,
    ) = """
        {
            "emergencyContactName": "$emergencyContactName",
            "emergencyContactPhone": "$emergencyContactPhone",
            "careNote": ${careNote?.let { "\"$it\"" } ?: "null"}
        }
        """

    // 동행 매니저 프로필 요청 본문
    private fun escortProfileBody(
        intro: String = "동행 매니저입니다.",
        bankName: String = "오픈은행",
        accountHolder: String = "김춘식",
        accountNumber: String = "123-0000000-123",
    ) = """
        {
            "intro": "$intro",
            "bankName": "$bankName",
            "accountHolder": "$accountHolder",
            "accountNumber": "$accountNumber"
        }
        """

    // 회원가입 요청
    private fun requestSignUp(body: String): ResultActions =
        mvc.perform(post("/api/v1/users").json(body)).andDo(print())

    // 본문으로 회원가입 후 accessToken 쿠키 반환
    private fun signUpWith(body: String): Cookie {
        val accessToken = requestSignUp(body)
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .getCookie("accessToken")

        assertThat(accessToken).isNotNull()
        return accessToken!!
    }

    // 내 정보 수정 요청
    private fun requestUpdateMe(accessToken: Cookie, body: String): ResultActions =
        mvc.perform(patch("/api/v1/users/profile").cookie(accessToken).json(body)).andDo(print())

    // 내 정보 수정 성공 응답 검증 (username, role, gender 는 바뀌지 않는다)
    private fun ResultActions.andExpectUpdatedMe(
        email: String = "first@test.test",
        name: String = "김춘식",
        birthDate: String = "1990-05-20",
        phoneNum: String = "010-1234-5678",
        region: String = "서울시",
    ): ResultActions = this
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value("200-4"))
        .andExpect(jsonPath("$.msg").value("회원 정보가 수정되었습니다."))
        .andExpect(jsonPath("$.data.username").value("user1"))
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.name").value(name))
        .andExpect(jsonPath("$.data.role").value("CLIENT"))
        .andExpect(jsonPath("$.data.gender").value("MALE"))
        .andExpect(jsonPath("$.data.birthDate").value(birthDate))
        .andExpect(jsonPath("$.data.phoneNum").value(phoneNum))
        .andExpect(jsonPath("$.data.region").value(region))
        .andExpect(jsonPath("$.data.createdAt").exists())
        .andExpect(jsonPath("$.data.password").doesNotExist())

    // 관리자 계정 주입
    private fun createTestAdmin() {
        val admin = User(
            ADMIN_USERNAME,
            passwordEncoder.encode(ADMIN_PASSWORD),
            "admin@admin.admin",
            "관리자",
            Role.ADMIN,
            Gender.MALE,
            LocalDate.of(2001, 1, 1),
            "010-9898-9898",
            "서울시",
        )
        userRepository.save(admin)
    }

    // 로그인 후 accessToken 쿠키 반환
    private fun login(username: String, password: String): Cookie {
        val accessToken = mvc.perform(
            post("/api/v1/auth/login").json(
                """
                {
                    "username": "$username",
                    "password": "$password"
                }
                """
            )
        )
            .andExpect(status().isOk())
            .andReturn()
            .response
            .getCookie("accessToken")

        assertThat(accessToken).isNotNull()
        return accessToken!!
    }

    // 관리자 로그인 (createTestAdmin() 호출 후 사용)
    private fun loginAsAdmin(): Cookie = login(ADMIN_USERNAME, ADMIN_PASSWORD)

    // 역할 지정 회원가입 후 accessToken 쿠키 반환 (기본은 의뢰인)
    private fun signUp(username: String, role: String = "CLIENT"): Cookie =
        signUpWith(
            signUpBody(
                username = username,
                password = "testPassword",
                email = "$username@user.user",
                role = role,
                // 전화번호 형식 검증(@Pattern)을 통과하면서 회원마다 겹치지 않는 번호
                phoneNum = "010-8080-%04d".format(PHONE_SEQ.incrementAndGet()),
            )
        )

    private fun findUserId(username: String): Long =
        userRepository.findByUsername(username)!!.id

    // 의뢰인 프로필 생성
    private fun createClientProfile(clientToken: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(clientToken)
                .json(clientProfileBody(careNote = "혼자 보행 불가"))
        )
            .andExpect(status().isCreated())
    }

    // 동행 매니저 프로필 생성
    private fun createEscortProfile(escortToken: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .json(escortProfileBody())
        )
            .andExpect(status().isCreated())
    }

    // 의뢰인의 공고 생성 (조회 권한 테스트용, 결제·이동수단 없이 공고만 저장)
    private fun createPost(clientUsername: String): Post {
        val client = userRepository.findByUsername(clientUsername)!!
        val now = LocalDateTime.now()

        return postRepository.save(
            Post(
                client = client,
                title = "병원 동행 구합니다",
                content = "정기 검진 동행",
                region = "서울시",
                hospitalName = "서울병원",
                hospitalAddress = "서울시 종로구",
                hospitalLat = BigDecimal("37.5665351"),
                hospitalLng = BigDecimal("126.9780000"),
                pickupAddress = "서울시 중구",
                pickupLat = BigDecimal("37.5600000"),
                pickupLng = BigDecimal("126.9900000"),
                hourlyPay = 15000,
                recruitStartAt = now.plusDays(1),
                recruitEndAt = now.plusDays(2),
                escortStartAt = now.plusDays(3),
                escortEndAt = now.plusDays(3).plusHours(2),
            )
        )
    }

    // 동행 매니저 지원 (PENDING 상태)
    private fun applyToPost(post: Post, escortUsername: String): Application {
        val escort = userRepository.findByUsername(escortUsername)!!

        return applicationRepository.save(Application(post, escort))
    }

    @Test
    @DisplayName("[UserController] 회원가입 -  정상 가입")
    fun t1() {
        val name = "김춘식"

        val resultActions = requestSignUp(
            signUpBody(
                username = "testUsername",
                password = "testPassword",
                email = "testEmail@test.test",
                name = name,
            )
        )

        resultActions
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("signUp"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-1"))
            .andExpect(jsonPath("$.msg").value("회원 가입이 완료되었습니다."))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.name").value(name))
            .andExpect(jsonPath("$.data.password").doesNotExist())
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 중복된 아이디로 가입 시 409 반환")
    fun t2() {
        fun body(email: String, phoneNum: String) =
            signUpBody(username = "testUsername", password = "testPassword", email = email, phoneNum = phoneNum)

        // 첫 번째 가입 성공
        requestSignUp(body("first@test.test", "010-1234-5678"))
            .andExpect(status().isCreated())

        // 같은 username, 다른 email로 가입 시도
        requestSignUp(body("second@test.test", "010-1734-5478"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-1"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 중복된 이메일로 가입 시 409 반환")
    fun t3() {
        fun body(username: String, phoneNum: String) =
            signUpBody(username = username, password = "testPassword", email = "testEmail@test.test", phoneNum = phoneNum)

        // 첫 번째 가입 성공
        requestSignUp(body("user1", "010-1234-5678"))
            .andExpect(status().isCreated())

        // 같은 email, 다른 username으로 가입 시도
        requestSignUp(body("user2", "010-1234-9999"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 중복된 전화번호로 가입 시 409 반환")
    fun t4() {
        fun body(username: String, email: String) =
            signUpBody(username = username, password = "testPassword", email = email, phoneNum = "010-1234-5678")

        // 첫 번째 가입 성공
        requestSignUp(body("user1", "first@test.test"))
            .andExpect(status().isCreated())

        // 다른 username, 다른 email, 같은 전화번호로 가입 시도
        requestSignUp(body("user2", "second@test.test"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-3"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 전화번호입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 필수값 누락 시 400-01 반환")
    fun t5() {
        // "username": "" 요청
        requestSignUp(signUpBody(username = "", password = "testPassword", email = "testEmail@test.test"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
            .andExpect(jsonPath("$.msg").value("username: 아이디는 필수 항목입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 존재하지 않는 gender 값으로 요청 시 400-02 반환")
    fun t6() {
        // "gender": "HELICOPTER"  요청
        requestSignUp(
            signUpBody(username = "user", password = "testPassword", email = "testEmail@test.test", gender = "HELICOPTER")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-02"))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 회원가입 시 쿠키 발급")
    fun t7() {
        // 회원 가입
        requestSignUp(signUpBody(username = "testUsername", password = "testPassword"))
            .andExpect(status().isCreated()) // 201 검증
            .andExpect(cookie().exists("accessToken")) // accessToken이 왔는지 검증
            .andExpect(cookie().exists("refreshToken")) // refreshToken이 왔는지 검증
            .andExpect(cookie().httpOnly("accessToken", true)) // js의 쿠키 접근 차단 검증
            .andExpect(cookie().path("accessToken", "/")) // access Token의 요청 Path가 전체인지 검증
            .andExpect(cookie().path("refreshToken", "/api/v1/auth")) // refresh Token의 요청 Path가 /api/v1/auth/refresh인지 검증
    }

    @Test
    @DisplayName("[UserController] 내 정보 조회 - 존재하는 아이디로 정상 로그인 후 내 정보 조회 요청")
    fun t11() {
        // 회원 가입
        requestSignUp(signUpBody())

        // 로그인
        val accessToken = login("user1", "pwd1")

        mvc.perform(get("/api/v1/users/profile").cookie(accessToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-3"))
            .andExpect(jsonPath("$.msg").value("내 정보 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.email").value("first@test.test"))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.role").value("CLIENT"))
            .andExpect(jsonPath("$.data.gender").value("MALE"))
            .andExpect(jsonPath("$.data.birthDate").value("1990-05-20"))
            .andExpect(jsonPath("$.data.phoneNum").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.suspendEndAt").value(nullValue()))
    }

    @Test
    @DisplayName("[UserController] 내 정보 조회 - 로그인 시도 없이 내 정보 조회 요청")
    fun t12() {
        mvc.perform(get("/api/v1/users/profile"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[UserController] 내 정보 조회 - 위조된 토큰으로 요청 시 401-112")
    fun t14() {
        val validToken = JwtProvider.toString(
            secretKey, 600,
            mapOf("id" to 1L, "username" to "user1", "role" to "CLIENT"),
        )

        // 서명 부분의 마지막 글자를 변경
        val forged = validToken.dropLast(1) + if (validToken.endsWith("A")) "B" else "A"

        mvc.perform(get("/api/v1/users/profile").cookie(Cookie("accessToken", forged)))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-112"))
            .andExpect(jsonPath("$.msg").value("유효하지 않은 토큰입니다."))
    }

    @Test
    @DisplayName("[UserController] Username 중복 검사 - 사용 가능한 username은 true")
    fun t15() {
        mvc.perform(get("/api/v1/users/username").param("username", "user1"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("사용 가능한 아이디입니다."))
            .andExpect(jsonPath("$.data").value("true"))
    }

    @Test
    @DisplayName("[UserController] Username 중복 검사 - 이미 존재하는 username은 false")
    fun t16() {
        // 회원 가입
        requestSignUp(signUpBody())

        mvc.perform(get("/api/v1/users/username").param("username", "user1"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 아이디입니다."))
            .andExpect(jsonPath("$.data").value("false"))
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환")
    fun t18() {
        // 회원 가입
        val accessToken = signUpWith(signUpBody())

        // 회원 정보 수정
        requestUpdateMe(
            accessToken,
            updateBody(
                password = "pwd123",
                email = "firssst@test.test",
                name = "김춘자",
                birthDate = "1990-05-29",
                phoneNum = "010-1334-5678",
                region = "경기도",
            ),
        ).andExpectUpdatedMe(
            email = "firssst@test.test",
            name = "김춘자",
            birthDate = "1990-05-29",
            phoneNum = "010-1334-5678",
            region = "경기도",
        )

        em.flush()
        em.clear()

        val updated = userRepository.findByUsername("user1")!!
        assertThat(updated.email).isEqualTo("firssst@test.test")
        assertThat(updated.name).isEqualTo("김춘자")
        assertThat(updated.birthDate).isEqualTo(LocalDate.of(1990, 5, 29))
        assertThat(passwordEncoder.matches("pwd1", updated.password)).isFalse()
        assertThat(updated.phoneNum).isEqualTo("010-1334-5678")
        assertThat(updated.region).isEqualTo("경기도")
        assertThat(passwordEncoder.matches("pwd123", updated.password)).isTrue() // 비밀번호를 수정 대상에 둔다면
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 지역만 수정")
    fun t19() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(region = "서울시이이이"))
            .andExpectUpdatedMe(region = "서울시이이이")
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 전화번호만 수정")
    fun t20() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(phoneNum = "010-3333-5678"))
            .andExpectUpdatedMe(phoneNum = "010-3333-5678")
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 생일만 수정")
    fun t21() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(birthDate = "1990-05-29"))
            .andExpectUpdatedMe(birthDate = "1990-05-29")
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 이름만 수정")
    fun t22() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(name = "김춘자"))
            .andExpectUpdatedMe(name = "김춘자")
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 이메일만 수정")
    fun t23() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(email = "huhuhuhuh@test.test"))
            .andExpectUpdatedMe(email = "huhuhuhuh@test.test")
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 정상 수정은 200-3 반환 - 비밀번호만 수정")
    fun t24() {
        val accessToken = signUpWith(signUpBody())

        requestUpdateMe(accessToken, updateBody(password = "pwd12222222222222222222"))
            .andExpectUpdatedMe()

        em.flush()
        em.clear()

        val updated = userRepository.findByUsername("user1")!!
        assertThat(updated.email).isEqualTo("first@test.test")
        assertThat(updated.name).isEqualTo("김춘식")
        assertThat(updated.birthDate).isEqualTo(LocalDate.of(1990, 5, 20))
        assertThat(passwordEncoder.matches("pwd12222222222222222222", updated.password)).isTrue()
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 존재하는 email로 수정 시도 시 409-2")
    fun t25() {
        // 회원 가입
        requestSignUp(signUpBody())
        val accessToken = signUpWith(
            signUpBody(username = "user13", email = "first1@test.test", phoneNum = "010-3333-5678")
        )

        // 회원 정보 수정 (user1 의 이메일로)
        requestUpdateMe(
            accessToken,
            updateBody(
                password = "pwd1222",
                email = "first@test.test",
                name = "김춘식22",
                birthDate = "1990-05-25",
                phoneNum = "010-3333-5678",
            ),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 존재하는 전화번호로 수정 시도 시 409-3")
    fun t26() {
        // 회원 가입
        requestSignUp(signUpBody())
        val accessToken = signUpWith(
            signUpBody(username = "user13", email = "first1@test.test", phoneNum = "010-3333-5678")
        )

        // 회원 정보 수정 (user1 의 전화번호로)
        requestUpdateMe(
            accessToken,
            updateBody(
                password = "pwd1222",
                email = "first3@test.test",
                name = "김춘식22",
                birthDate = "1990-05-25",
                phoneNum = "010-1234-5678",
            ),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-3"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 전화번호입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원 탈퇴 - 자기 자신의 계정을 정상적으로 탈퇴하는 경우 204-1 반환")
    fun t27() {
        // 회원 가입
        val accessToken = signUpWith(signUpBody())
        val userId = findUserId("user1")

        // 회원 탈퇴
        mvc.perform(delete("/api/v1/users/profile").cookie(accessToken))
            .andDo(print())
            .andExpect(status().isNoContent())
            .andExpect(jsonPath("$.statusCode").value("204-1"))
            .andExpect(jsonPath("$.msg").value("회원 탈퇴가 완료되었습니다."))
            .andExpect { result ->
                // accessToken 폐기 확인
                val newAccessToken = result.response.getCookie("accessToken")!!
                assertThat(newAccessToken.value).isEmpty()
                assertThat(newAccessToken.maxAge).isEqualTo(0)
                assertThat(newAccessToken.path).isEqualTo("/")
                assertThat(newAccessToken.isHttpOnly).isTrue()

                // refreshToken 폐기 확인
                val newRefreshToken = result.response.getCookie("refreshToken")!!
                assertThat(newRefreshToken.value).isEmpty()
                assertThat(newRefreshToken.maxAge).isEqualTo(0)
                assertThat(newRefreshToken.path).isEqualTo("/api/v1/auth")
            }

        em.flush()
        em.clear()

        val deletedUser = em.createNativeQuery("SELECT * FROM users WHERE id = :id", User::class.java)
            .setParameter("id", userId)
            .singleResult as User

        assertThat(deletedUser.email).isEqualTo("deleted_$userId")
        assertThat(deletedUser.username).isEqualTo("deleted_$userId")
        assertThat(deletedUser.phoneNum).isEqualTo("deleted_$userId")
        assertThat(deletedUser.deletedAt).isNotNull()

        val refreshTokenList = refreshTokenRepository.findAllByUserId(userId)

        assertThat(refreshTokenList).isNotEmpty()
        refreshTokenList.forEach { assertThat(it.isRevoked).isTrue() }
    }

    @Test
    @DisplayName("[UserController] 회원 탈퇴 - 탈퇴 후 기존 accessToken으로 요청 시 401")
    fun t28() {
        // 회원 가입 (탈취된 토큰이라고 가정하고 미리 보관)
        val accessToken = signUpWith(signUpBody())

        // 회원 탈퇴
        mvc.perform(delete("/api/v1/users/profile").cookie(accessToken))
            .andExpect(status().isNoContent())

        // 실제 서버처럼 다음 요청이 새 영속성 컨텍스트에서 시작되도록
        em.flush()
        em.clear()

        // 탈퇴 전에 받은 토큰으로 다시 요청
        mvc.perform(get("/api/v1/users/profile").cookie(accessToken))
            .andDo(print())
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("[UserController] 회원 탈퇴 - 탈퇴 후 같은 아이디, 이메일, 전화번호로 재가입 가능")
    fun t29() {
        val signUpBody = signUpBody()

        // 회원 가입
        val accessToken = signUpWith(signUpBody)

        // 회원 탈퇴
        mvc.perform(delete("/api/v1/users/profile").cookie(accessToken))
            .andExpect(status().isNoContent())

        em.flush()
        em.clear()

        // 같은 정보로 재가입
        requestSignUp(signUpBody)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-1"))
    }

    @Test
    @DisplayName("[UserController] 회원가입 - 대소문자만 다른 이메일로 가입 시 409-2")
    fun t30() {
        // 첫 번째 가입
        requestSignUp(signUpBody(username = "user1", password = "testPassword", email = "first@test.test", phoneNum = "010-1234-5678"))
            .andExpect(status().isCreated())

        // 대문자로 바꾼 같은 이메일로 가입 시도
        requestSignUp(signUpBody(username = "user2", password = "testPassword", email = "FIRST@TEST.TEST", phoneNum = "010-9999-5678"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 대소문자만 다른 이메일로 수정 시 409-2")
    fun t31() {
        // user1 가입
        signUpWith(signUpBody(username = "user1", email = "first@test.test", phoneNum = "010-1234-5678"))

        // user2 가입
        val accessToken = signUpWith(signUpBody(username = "user2", email = "second@test.test", phoneNum = "010-3333-5678"))

        // user2가 user1의 이메일을 대문자로 바꿔서 수정 시도
        requestUpdateMe(accessToken, updateBody(email = "FIRST@TEST.TEST", phoneNum = "010-3333-5678"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("[UserController] 회원 탈퇴 - 로그인 없이 탈퇴 요청 시 401-113")
    fun t32() {
        mvc.perform(delete("/api/v1/users/profile"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 생성 - 의뢰인의 정상 프로필 생성 시 201-2 반환")
    fun t33() {
        val userToken = signUp("user1")
        val userId = findUserId("user1")

        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(userToken)
                .json(clientProfileBody(careNote = "여기 아프고 저기 아프고 레전드 아픔. 혼자 보행 불가합니다."))
        )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-2"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 생성되었습니다."))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.careNote").value("여기 아프고 저기 아프고 레전드 아픔. 혼자 보행 불가합니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 생성 - 특이사항이 null이여도 정상 생성")
    fun t34() {
        val userToken = signUp("user1")
        val userId = findUserId("user1")

        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(userToken)
                .json(clientProfileBody(careNote = null))
        )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-2"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 생성되었습니다."))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.careNote").value("특이사항 없음"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 생성 - 특이사항이 공백이여도 정상 생성")
    fun t35() {
        val userToken = signUp("user1")
        val userId = findUserId("user1")

        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(userToken)
                .json(clientProfileBody(careNote = ""))
        )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-2"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 생성되었습니다."))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.careNote").value("특이사항 없음"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신의 프로필 조회 - 로그인 후 자신의 프로필 조회 시 200-5 반환")
    fun t36() {
        val userToken = signUp("user1")
        val userId = findUserId("user1")

        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(userToken)
                .json(clientProfileBody(careNote = "졸리다..."))
        )
            .andDo(print())
            .andExpect(status().isCreated())

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/profile/client").cookie(userToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-5"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필 조회를 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.careNote").value("졸리다..."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신의 프로필 조회 - 프로필이 없으면 404 반환")
    fun t37() {
        val userToken = signUp("user1")

        mvc.perform(get("/api/v1/users/profile/client").cookie(userToken))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 관리자 조회 시 200-6 반환")
    fun t38() {
        createTestAdmin()
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val adminToken = loginAsAdmin()

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(adminToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-6"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.userId").value(clientId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.careNote").value("혼자 보행 불가"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 매칭된 동행 매니저 조회 시 200-6 반환")
    fun t39() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val escortToken = signUp("escort1", "ESCORT")

        val post = createPost("client1")
        val application = applyToPost(post, "escort1")

        // 매칭 확정
        application.accept()
        post.match()

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(escortToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-6"))
            .andExpect(jsonPath("$.data.userId").value(clientId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("김철수"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 지원만 한 동행 매니저 조회 시 403-2 반환")
    fun t40() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val escortToken = signUp("escort1", "ESCORT")

        // 지원만 하고 승인 전 (PENDING)
        val post = createPost("client1")
        applyToPost(post, "escort1")

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(escortToken))
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-2"))
            .andExpect(jsonPath("$.msg").value("매칭된 의뢰인의 프로필만 조회할 수 있습니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 동행 완료 후 동행 매니저 조회 시 403-2 반환")
    fun t41() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val escortToken = signUp("escort1", "ESCORT")

        val post = createPost("client1")
        val application = applyToPost(post, "escort1")

        // 매칭 → 동행 완료
        application.accept()
        post.match()
        post.startProgress(LocalDateTime.now().minusHours(1)) // 매칭 완료 → 동행 진행 중 → 동행 완료 순서를 지킨다
        post.complete(LocalDateTime.now())

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(escortToken))
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-2"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 매칭이 취소된 동행 매니저 조회 시 403-2 반환")
    fun t42() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val escortToken = signUp("escort1", "ESCORT")

        val post = createPost("client1")
        val application = applyToPost(post, "escort1")

        // 매칭 → 의뢰인이 매칭 취소 (지원은 ACCEPTED로 남음)
        application.accept()
        post.match()
        post.matchedCancel()

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(escortToken))
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-2"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 매칭 이력 없는 동행 매니저 조회 시 403-2 반환")
    fun t43() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")
        val escortToken = signUp("escort1", "ESCORT")

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(escortToken))
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-2"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 의뢰인이 다른 의뢰인 조회 시 403-1 반환")
    fun t44() {
        val client1Token = signUp("client1")
        createClientProfile(client1Token)
        val client1Id = findUserId("client1")
        val client2Token = signUp("client2")

        mvc.perform(get("/api/v1/users/{userId}/profile/client", client1Id).cookie(client2Token))
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 프로필 없는 의뢰인 조회 시 404 반환")
    fun t45() {
        createTestAdmin()
        signUp("client1") // 프로필 생성 안 함
        val clientId = findUserId("client1")
        val adminToken = loginAsAdmin()

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId).cookie(adminToken))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 프로필 타인 조회 - 로그인 없이 조회 시 401-113 반환")
    fun t46() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)
        val clientId = findUserId("client1")

        mvc.perform(get("/api/v1/users/{userId}/profile/client", clientId))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신 프로필 수정 - 정상적으로 존재하는 자기 자신의 프로필 수정 시 200-7 반환")
    fun t47() {
        val clientToken = signUp("client1")
        val clientId = findUserId("client1")
        createClientProfile(clientToken)

        mvc.perform(
            put("/api/v1/users/profile/client")
                .cookie(clientToken)
                .json(clientProfileBody("정상수", "010-9999-9999", "왤케 안끝나"))
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-7"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필 수정을 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(clientId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("정상수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-9999-9999"))
            .andExpect(jsonPath("$.data.careNote").value("왤케 안끝나"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신 프로필 수정 - 정상적으로 존재하는 자기 자신의 프로필 수정 && careNote가 null 200-7 반환")
    fun t48() {
        val clientToken = signUp("client1")
        val clientId = findUserId("client1")
        createClientProfile(clientToken)

        mvc.perform(
            put("/api/v1/users/profile/client")
                .cookie(clientToken)
                .json(clientProfileBody("정상수", "010-9999-9999", null))
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-7"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필 수정을 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(clientId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("정상수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-9999-9999"))
            .andExpect(jsonPath("$.data.careNote").value("특이사항 없음"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신 프로필 수정 - 정상적으로 존재하는 자기 자신의 프로필 수정 && careNote가 공백 200-7 반환")
    fun t49() {
        val clientToken = signUp("client1")
        val clientId = findUserId("client1")
        createClientProfile(clientToken)

        mvc.perform(
            put("/api/v1/users/profile/client")
                .cookie(clientToken)
                .json(clientProfileBody("정상수", "010-9999-9999", ""))
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-7"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필 수정을 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(clientId))
            .andExpect(jsonPath("$.data.emergencyContactName").value("정상수"))
            .andExpect(jsonPath("$.data.emergencyContactPhone").value("010-9999-9999"))
            .andExpect(jsonPath("$.data.careNote").value("특이사항 없음"))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신 프로필 수정 - 프로필을 생성하지 않은 유저의 프로필 수정 요청 시 404 반환")
    fun t50() {
        val clientToken = signUp("client1")

        mvc.perform(
            put("/api/v1/users/profile/client")
                .cookie(clientToken)
                .json(clientProfileBody("정상수", "010-9999-9999", ""))
        )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-2"))
            .andExpect(jsonPath("$.msg").value("의뢰인 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 의뢰인 자기 자신 프로필 수정 - 로그인 없이 수정 시도 시 401-113 반환")
    fun t51() {
        val clientToken = signUp("client1")
        createClientProfile(clientToken)

        mvc.perform(
            put("/api/v1/users/profile/client")
                .json(clientProfileBody("정상수", "010-9999-9999", ""))
        )
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 생성 - 정상 생성 시 201-3 반환")
    fun t52() {
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")

        mvc.perform(
            post("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .json(escortProfileBody())
        )
            .andDo(print())
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("createProfileEscort"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-3"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 생성되었습니다."))
            .andExpect(jsonPath("$.data.userId").value(escortId))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.intro").value("동행 매니저입니다."))
            .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.verified").value(false))
            .andExpect(jsonPath("$.data.bankName").value("오픈은행"))
            .andExpect(jsonPath("$.data.accountHolder").value("김춘식"))
            .andExpect(jsonPath("$.data.accountNumber").value("123-0000000-123"))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 조회 - 본인 조회 시 계좌 포함 200-8 반환")
    fun t53() {
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")
        createEscortProfile(escortToken)

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/profile/escort").cookie(escortToken))
            .andDo(print())
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("getProfileEscort"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-8"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필 조회를 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(escortId))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.intro").value("동행 매니저입니다."))
            .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.verified").value(false))
            .andExpect(jsonPath("$.data.bankName").value("오픈은행"))
            .andExpect(jsonPath("$.data.accountHolder").value("김춘식"))
            .andExpect(jsonPath("$.data.accountNumber").value("123-0000000-123"))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 조회 - 프로필이 없으면 404 반환")
    fun t54() {
        val escortToken = signUp("escort1", "ESCORT")

        mvc.perform(get("/api/v1/users/profile/escort").cookie(escortToken))
            .andDo(print())
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("getProfileEscort"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-3"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 타인 조회 - 의뢰인 조회 시 공개 정보만 200-9 반환")
    fun t55() {
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")
        createEscortProfile(escortToken)
        val clientToken = signUp("client1")

        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/{userId}/profile/escort", escortId).cookie(clientToken))
            .andDo(print())
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("getProfileEscortByClient"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-9"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필 조회를 완료했습니다."))
            // 공개 정보
            .andExpect(jsonPath("$.data.userId").value(escortId))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.intro").value("동행 매니저입니다."))
            .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.verified").value(false))
            // 계좌 정보는 노출되면 안 됨
            .andExpect(jsonPath("$.data.bankName").doesNotExist())
            .andExpect(jsonPath("$.data.accountHolder").doesNotExist())
            .andExpect(jsonPath("$.data.accountNumber").doesNotExist())
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 타인 조회 - 프로필 없는 동행 매니저 조회 시 404 반환")
    fun t56() {
        signUp("escort1", "ESCORT") // 프로필 생성 안 함
        val escortId = findUserId("escort1")
        val clientToken = signUp("client1")

        mvc.perform(get("/api/v1/users/{userId}/profile/escort", escortId).cookie(clientToken))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 수정 - 존재하는 프로필에 대해 정상 수정 요청 시 200-10 반환")
    fun t57() {
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")
        createEscortProfile(escortToken)

        mvc.perform(
            put("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .json(ESCORT_UPDATE_BODY)
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-10"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필 수정을 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(escortId))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.intro").value("수정한 자기소개입니다."))
            .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.verified").value(false))
            .andExpect(jsonPath("$.data.bankName").value("수정은행"))
            .andExpect(jsonPath("$.data.accountHolder").value("김수정"))
            .andExpect(jsonPath("$.data.accountNumber").value("123-0012300-123"))

        em.flush()
        em.clear()

        val updated = escortProfileRepository.findByIdOrNull(escortId)!!
        assertThat(updated.intro).isEqualTo("수정한 자기소개입니다.")
        assertThat(updated.accountNumber).isEqualTo("123-0012300-123")
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 수정 - 존재하지 않는 프로필에 대해 수정 요청 시 404 반환")
    fun t58() {
        val escortToken = signUp("escort1", "ESCORT")

        mvc.perform(
            put("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .json(ESCORT_UPDATE_BODY)
        )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-3"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[UserController] 동행 매니저 프로필 수정 - 로그인 없이 수정 요청 시 401-113 반환")
    fun t59() {
        signUp("escort1", "ESCORT")

        mvc.perform(put("/api/v1/users/profile/escort").json(ESCORT_UPDATE_BODY))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[UserController] email 중복 검사 - 사용 가능한 email은 true")
    fun t60() {
        mvc.perform(get("/api/v1/users/email").param("email", "user1@test.com"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-2"))
            .andExpect(jsonPath("$.msg").value("사용 가능한 이메일입니다."))
            .andExpect(jsonPath("$.data").value("true"))
    }

    @Test
    @DisplayName("[UserController] email 중복 검사 - 이미 존재하는 email은 false")
    fun t61() {
        // 회원 가입
        requestSignUp(signUpBody())

        mvc.perform(get("/api/v1/users/email").param("email", "first@test.test"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-2"))
            .andExpect(jsonPath("$.msg").value("이미 사용 중인 이메일입니다."))
            .andExpect(jsonPath("$.data").value("false"))
    }

    @Test
    @DisplayName("[UserController] 회원가입 시 User의 userStatus가 기본적으로 ACTIVE로 설정")
    fun t62() {
        signUp("test1", "ESCORT")

        em.flush()
        em.clear()

        val test = userRepository.findByUsername("test1")!!

        assertThat(test.userStatus).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    @DisplayName("[UserController] 회원가입 시 UserStatusHistory가 ACTIVE로 기록 생성")
    fun t63() {
        signUp("test1", "ESCORT")

        em.flush()
        em.clear()

        val test = userRepository.findByUsername("test1")!!

        val history = userStatusHistoryRepository.findAllByUserId(test.id)[0]
        assertThat(history.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(history.reason).isEqualTo("회원가입")
        assertThat(history.adminId).isNull()
        assertThat(history.endAt).isNull()
        assertThat(history.createdAt).isNotNull()
    }

    @Test
    @DisplayName("[UserController] 내 정보 조회 - 기간 정지된 회원은 userStatus, suspendEndAt, statusReason 반환")
    fun t64() {
        val accessToken = signUp("user1")

        val user = userRepository.findByUsername("user1")!!
        userStatusHistoryRepository.save(user.suspend(1L, "노쇼 3회 누적"))
        em.flush()
        em.clear()

        mvc.perform(get("/api/v1/users/profile").cookie(accessToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-3"))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.suspendEndAt").isNotEmpty())
            .andExpect(jsonPath("$.data.statusReason").value("노쇼 3회 누적"))
    }

    @Test
    @DisplayName("[UserController] 회원 정보 수정 - 기간 정지된 회원은 수정 응답에도 정지 정보 반환")
    fun t65() {
        val accessToken = signUp("user1")

        val user = userRepository.findByUsername("user1")!!
        userStatusHistoryRepository.save(user.suspend(1L, "노쇼 3회 누적"))
        em.flush()
        em.clear()

        requestUpdateMe(
            accessToken,
            updateBody(
                password = "newPassword",
                email = "changed@user.user",
                name = "김춘자",
                birthDate = "1990-05-29",
                phoneNum = "010-1334-5678",
                region = "경기도",
            ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-4"))
            .andExpect(jsonPath("$.data.name").value("김춘자"))
            .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.suspendEndAt").isNotEmpty())
            .andExpect(jsonPath("$.data.statusReason").value("노쇼 3회 누적"))
    }

    companion object {
        private const val ADMIN_USERNAME = "adminTest"
        private const val ADMIN_PASSWORD = "adminTest"

        // signUp 도우미가 회원마다 서로 다른 전화번호를 만들기 위한 일련번호
        private val PHONE_SEQ = AtomicInteger()

        // 동행 매니저 프로필 수정 요청 본문 (t57 ~ t59 공통)
        private const val ESCORT_UPDATE_BODY = """
            {
                "intro": "수정한 자기소개입니다.",
                "bankName": "수정은행",
                "accountHolder": "김수정",
                "accountNumber": "123-0012300-123"
            }
            """
    }
}
