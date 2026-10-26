package com.back.nbe12142team06.domain.user.controller

import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository
import com.jayway.jsonpath.JsonPath
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository

    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    private var phoneSeq = 0

    // 계정 상태 변경 요청 (기간 정지 / 영구 정지 / 정지 해제). token 이 null 이면 비로그인 요청
    private fun requestStatusChange(action: String, userId: Long, reason: String, token: Cookie?): ResultActions {
        val request = post("/api/v1/admin/users/{userId}/$action", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                    "reason": "$reason"
                }
                """
            )
        token?.let { request.cookie(it) }

        return mvc.perform(request).andDo(print())
    }

    // 정지 해제 요청
    private fun requestRelease(userId: Long, token: Cookie?) =
        requestStatusChange("release", userId, "관리자 실수 ㅈㅅㅈㅅ", token)

    // 기간 정지 요청
    private fun requestSuspension(userId: Long, token: Cookie?) =
        requestStatusChange("suspension", userId, "노쇼 3회 누적", token)

    // 영구 정지 요청
    private fun requestBan(userId: Long, token: Cookie?) =
        requestStatusChange("ban", userId, "욕설", token)

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
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
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

    // 역할 지정 회원가입 후 accessToken 쿠키 반환 (가입 시 토큰이 발급됨)
    private fun signUp(username: String, role: String = "CLIENT"): Cookie {
        // 전화번호 형식 검증(@Pattern)을 통과하면서 회원마다 겹치지 않는 번호
        val phoneNum = "010-8080-%04d".format(++phoneSeq)

        val accessToken = mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "testPassword",
                        "email": "$username@user.user",
                        "name": "김춘식",
                        "role": "$role",
                        "gender": "MALE",
                        "birthDate": "1990-05-20",
                        "phoneNum": "$phoneNum",
                        "region": "서울시"
                    }
                    """
                )
        )
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .getCookie("accessToken")

        assertThat(accessToken).isNotNull()
        return accessToken!!
    }

    // 동행 매니저 프로필 생성
    private fun createEscortProfile(escortToken: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "intro": "동행 매니저입니다.",
                        "bankName": "오픈은행",
                        "accountHolder": "김춘식",
                        "accountNumber": "123-0000000-123"
                    }
                    """
                )
        )
            .andExpect(status().isCreated())
    }

    // 공고 등록 요청 (모집 시작은 내일, 동행은 모집 마감 1시간 뒤부터 2시간)
    private fun requestWritePost(clientToken: Cookie): ResultActions {
        val recruitStartAt = LocalDateTime.now().plusDays(1)
        val recruitEndAt = recruitStartAt.plusHours(1)
        val escortStartAt = recruitEndAt.plusHours(1)
        val escortEndAt = escortStartAt.plusHours(2)

        return mvc.perform(
            post("/api/v1/posts")
                .cookie(clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "title": "병원 동행 구합니다",
                        "content": "정기 검진 동행 부탁드립니다",
                        "region": "서울",
                        "hospitalName": "서울대병원",
                        "hospitalAddress": "서울 종로구 대학로 101",
                        "hospitalLat": 37.5796,
                        "hospitalLng": 126.9990,
                        "pickupAddress": "서울 종로구 혜화동 1",
                        "pickupLat": 37.5822,
                        "pickupLng": 127.0018,
                        "hourlyPay": 15000,
                        "recruitStartAt": "$recruitStartAt",
                        "recruitEndAt": "$recruitEndAt",
                        "escortStartAt": "$escortStartAt",
                        "escortEndAt": "$escortEndAt",
                        "rideSelectToHospital": "TAXI",
                        "rideSelectToHome": "TAXI",
                        "patientNote": null,
                        "reportRequired": true
                    }
                    """
                )
        ).andDo(print())
    }

    // 공고 등록 후, 동행인이 바로 지원할 수 있도록 모집 시작 시간을 과거로 당긴 공고 ID 반환
    private fun writeOpenPost(clientToken: Cookie): Long {
        val writeResponse = requestWritePost(clientToken)
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)

        val postId = JsonPath.read<Number>(writeResponse, "$.data.id").toLong()

        // 모집 기간 안으로 들어오도록 모집 시작 시간을 과거로 변경
        val savedPost = postRepository.findById(postId).orElseThrow()
        ReflectionTestUtils.setField(savedPost, "recruitStartAt", LocalDateTime.now().minusMinutes(1))
        em.flush()

        return postId
    }

    private fun findUserId(username: String): Long =
        userRepository.findByUsername(username)!!.id

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 관리자가 정상적으로 단건 조회 시 200-1 반환")
    fun t1() {
        createTestAdmin()
        signUp("user1")
        val user1Id = findUserId("user1")
        val adminToken = loginAsAdmin()

        // 회원 단건 조회
        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.id").value(user1Id))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.email").value("user1@user.user"))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.role").value("CLIENT"))
            .andExpect(jsonPath("$.data.gender").value("MALE"))
            .andExpect(jsonPath("$.data.birthDate").value("1990-05-20"))
            .andExpect(jsonPath("$.data.phoneNum").value("010-8080-0001"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.deletedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.suspendEndAt").value(nullValue()))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 탈퇴한 회원도 관리자는 조회할 수 있고 200-1 반환")
    fun t2() {
        createTestAdmin()
        val user1Token = signUp("user1")
        val user1Id = findUserId("user1")  // 탈퇴 후에는 조회되지 않으므로 먼저 확보

        // user1 탈퇴
        mvc.perform(
            delete("/api/v1/users/profile")
                .cookie(user1Token)
        )
            .andExpect(status().isNoContent())

        val adminToken = loginAsAdmin()

        // 탈퇴 회원 단건 조회
        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.id").value(user1Id))
            .andExpect(jsonPath("$.data.username").value("deleted_$user1Id"))
            .andExpect(jsonPath("$.data.deletedAt").exists())
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 로그인 없이 조회 시 401-113 반환")
    fun t3() {
        createTestAdmin()
        signUp("user1")
        val user1Id = findUserId("user1")

        // 쿠키 없이 조회
        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
        ).andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 일반 회원이 조회 시 403-1 반환")
    fun t4() {
        val user1Token = signUp("user1")
        val user1Id = findUserId("user1")

        // 일반 회원 토큰으로 조회 (자기 자신이어도 관리자 API는 불가)
        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
                .cookie(user1Token)
        ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 존재하지 않는 회원 조회 시 404 반환")
    fun t5() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        // 존재하지 않는 id로 조회
        mvc.perform(
            get("/api/v1/admin/users/{id}", Long.MAX_VALUE)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 관리자가 정상 조회 시 200 반환")
    fun t6() {
        // 관리자 로그인
        createTestAdmin()
        val adminToken = loginAsAdmin()

        // 다수의 회원 생성
        for (i in 1..19) {
            signUp("user$i")
        }

        val resultActions = mvc.perform(
            get("/api/v1/admin/users")
                .cookie(adminToken)
        ).andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-2"))
            .andExpect(jsonPath("$.msg").value("회원 목록 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(10))
            .andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.totalElements").value(20))  // 관리자 1 + 회원 19
            .andExpect(jsonPath("$.data.totalPages").value(2))
            // 최신 가입자가 맨 앞
            .andExpect(jsonPath("$.data.content[0].username").value("user19"))
            .andExpect(jsonPath("$.data.content[9].username").value("user10"))

        // 생성일 내림차순 검증
        val responseBody = resultActions.andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)

        val createdAts = JsonPath.read<List<String>>(responseBody, "$.data.content[*].createdAt")
            .map(LocalDateTime::parse)

        assertThat(createdAts).isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 음수 페이지 요청 시 400-1 반환")
    fun t7() {
        // 관리자 로그인
        createTestAdmin()
        val adminToken = loginAsAdmin()

        // 다수의 회원 생성
        for (i in 1..3) {
            signUp("user$i")
        }

        mvc.perform(
            get("/api/v1/admin/users")
                .param("page", "-1")
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-4"))
            .andExpect(jsonPath("$.msg").value("페이지 번호는 음수일 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 로그인 없이 요청 시 401-113 반환")
    fun t8() {
        // 다수의 회원 생성
        for (i in 1..3) {
            signUp("user$i")
        }

        mvc.perform(
            get("/api/v1/admin/users")
                .param("page", "-1")
        ).andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 일반 회원이 조회 시 403-1 반환")
    fun t9() {
        val user1Token = signUp("user1")

        // 다수의 회원 생성
        for (i in 2..3) {
            signUp("user$i")
        }

        mvc.perform(
            get("/api/v1/admin/users")
                .param("page", "1")
                .cookie(user1Token)
        ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 관리자가 정상 수정 시 200-3 반환")
    fun t10() {
        createTestAdmin()
        signUp("user1")
        val user1Id = findUserId("user1")
        val adminToken = loginAsAdmin()

        // 회원 정보 수정
        mvc.perform(
            patch("/api/v1/admin/users/{id}", user1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-3"))
            .andExpect(jsonPath("$.msg").value("회원 정보가 수정되었습니다."))
            .andExpect(jsonPath("$.data.id").value(user1Id))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.email").value("updated@user.user"))
            .andExpect(jsonPath("$.data.name").value("김춘자"))
            .andExpect(jsonPath("$.data.birthDate").value("1995-03-15"))
            .andExpect(jsonPath("$.data.phoneNum").value("010-1111-2222"))
            .andExpect(jsonPath("$.data.region").value("경기도"))
            .andExpect(jsonPath("$.data.deleted").value(false))
            .andExpect(jsonPath("$.data.password").doesNotExist())

        // 비밀번호 유지 확인 (기존 비밀번호로 로그인 성공)
        login("user1", "testPassword")
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 탈퇴한 회원 수정 시 400-2 반환")
    fun t11() {
        createTestAdmin()
        val userToken = signUp("user1")
        val user1Id = findUserId("user1")
        val adminToken = loginAsAdmin()

        // 회원 탈퇴
        mvc.perform(
            delete("/api/v1/users/profile")
                .cookie(userToken)
        )
            .andExpect(status().isNoContent())

        // 회원 정보 수정
        mvc.perform(
            patch("/api/v1/admin/users/{id}", user1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-2"))
            .andExpect(jsonPath("$.msg").value("탈퇴한 회원의 정보는 수정할 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 로그인 없이 수정 시 401-113 반환")
    fun t12() {
        signUp("user1")
        val user1Id = findUserId("user1")

        // 쿠키 없이 수정
        mvc.perform(
            patch("/api/v1/admin/users/{id}", user1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY)
        ).andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 일반 회원이 수정 시 403-1 반환")
    fun t13() {
        signUp("user1")
        val user1Id = findUserId("user1")
        val user2Token = signUp("user2")

        // 일반 회원 토큰으로 다른 회원 수정
        mvc.perform(
            patch("/api/v1/admin/users/{id}", user1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY)
                .cookie(user2Token)
        ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 관리자의 정상 탈퇴 요청 시 200-4 반환")
    fun t14() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        mvc.perform(
            delete("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-4"))
            .andExpect(jsonPath("$.msg").value("회원 탈퇴가 완료되었습니다."))

        em.flush()
        em.clear()

        val deletedUser = userRepository.findByIdIncludingDeleted(user1Id)!!

        assertThat(deletedUser.email).isEqualTo("deleted_$user1Id")
        assertThat(deletedUser.username).isEqualTo("deleted_$user1Id")
        assertThat(deletedUser.phoneNum).isEqualTo("deleted_$user1Id")
        assertThat(deletedUser.deletedAt).isNotNull()

        val refreshTokenList = refreshTokenRepository.findAllByUserId(user1Id)

        assertThat(refreshTokenList).isNotEmpty()
        refreshTokenList.forEach { assertThat(it.isRevoked).isTrue() }
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 존재하지 않는 회원 탈퇴 요청 시 404 반환")
    fun t15() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        mvc.perform(
            delete("/api/v1/admin/users/{id}", Long.MAX_VALUE)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 관리자가 자신의 계정에 대한 탈퇴 요청 시 400-3")
    fun t16() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        mvc.perform(
            delete("/api/v1/admin/users/{id}", adminId)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-3"))
            .andExpect(jsonPath("$.msg").value("관리자는 자신의 계정을 탈퇴시킬 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 이미 탈퇴한 회원에 대한 탈퇴 요청 시 400-4 반환")
    fun t17() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        // 정상 탈퇴
        mvc.perform(
            delete("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-4"))
            .andExpect(jsonPath("$.msg").value("회원 탈퇴가 완료되었습니다."))

        // 또또 탈퇴 요청
        mvc.perform(
            delete("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-5"))
            .andExpect(jsonPath("$.msg").value("이미 탈퇴한 회원입니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 동행 매니저 프로필 타인 조회 - 관리자 조회 시 계좌 정보까지 200-9 반환")
    fun t18() {
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")
        createEscortProfile(escortToken)

        createTestAdmin()
        val adminToken = loginAsAdmin()

        em.flush()
        em.clear()

        mvc.perform(
            get("/api/v1/admin/users/{userId}/profile/escort", escortId)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-9"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필 조회를 완료했습니다."))
            .andExpect(jsonPath("$.data.userId").value(escortId))
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(jsonPath("$.data.region").value("서울시"))
            .andExpect(jsonPath("$.data.intro").value("동행 매니저입니다."))
            .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.verified").value(false))
            // 계좌 정보 포함
            .andExpect(jsonPath("$.data.bankName").value("오픈은행"))
            .andExpect(jsonPath("$.data.accountHolder").value("김춘식"))
            .andExpect(jsonPath("$.data.accountNumber").value("123-0000000-123"))
    }

    @Test
    @DisplayName("[AdminUserController] 동행 매니저 프로필 타인 조회 - 프로필이 없으면 404 반환")
    fun t19() {
        signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")

        createTestAdmin()
        val adminToken = loginAsAdmin()

        em.flush()
        em.clear()

        mvc.perform(
            get("/api/v1/admin/users/{userId}/profile/escort", escortId)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-3"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 관리자가 ACTIVE 상태의 유저를 정지 요청 시 유저 상태를 SUSPENDED로 변경 후 200-10 반환")
    fun t20() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        signUp("user1")
        val user1Id = findUserId("user1")

        requestSuspension(user1Id, adminToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-10"))
            .andExpect(jsonPath("$.msg").value("회원이 기간 정지되었습니다."))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.startAt").isNotEmpty())
            .andExpect(jsonPath("$.data.endAt").isNotEmpty())
            .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"))

        em.flush()
        em.clear()

        val user = userRepository.findById(user1Id).orElseThrow()
        assertThat(user.userStatus).isEqualTo(UserStatus.SUSPENDED)

        val history = userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow()
        assertThat(history.status).isEqualTo(UserStatus.SUSPENDED)
        assertThat(history.adminId).isEqualTo(adminId)
        assertThat(history.reason).isEqualTo("노쇼 3회 누적")
        assertThat(history.endAt).isNotNull()
        assertThat(history.endAt!!.minute).isZero()
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 이미 기간 정지된 회원을 정지 요청 시 400-8 반환")
    fun t21() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        // 첫 번째 정지
        requestSuspension(user1Id, adminToken)
            .andExpect(status().isOk())

        // 두 번째 정지
        requestSuspension(user1Id, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-8"))
            .andExpect(jsonPath("$.msg").value("이미 기간 정지 상태인 계정입니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 영구 정지된 회원을 정지 요청 시 400-9 반환")
    fun t22() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        signUp("user1")
        val user1Id = findUserId("user1")

        val user = userRepository.findById(user1Id).orElseThrow()
        userStatusHistoryRepository.save(user.ban(adminId, "영구 정지 테스트"))
        em.flush()
        em.clear()

        requestSuspension(user1Id, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-9"))
            .andExpect(jsonPath("$.msg").value("영구 정지된 계정은 해제만 가능합니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 관리자 계정을 정지 요청 시 400-6 반환")
    fun t23() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        requestSuspension(adminId, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-6"))
            .andExpect(jsonPath("$.msg").value("관리자의 계정을 정지시킬 수 없습니다."))

        // 상태 유지 확인
        em.flush()
        em.clear()

        val admin = userRepository.findById(adminId).orElseThrow()
        assertThat(admin.userStatus).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 존재하지 않는 회원을 정지 요청 시 404-1 반환")
    fun t24() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        requestSuspension(Long.MAX_VALUE, adminToken)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 로그인 없이 요청 시 401-113 반환")
    fun t25() {
        signUp("user1")
        val user1Id = findUserId("user1")

        requestSuspension(user1Id, null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 일반 회원이 요청 시 403-1 반환")
    fun t26() {
        signUp("user1")
        val user1Id = findUserId("user1")
        val user2Token = signUp("user2")

        requestSuspension(user1Id, user2Token)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 관리자가 ACTIVE 상태의 유저를 정지 요청 시 유저 상태를 BANNED로 변경 후 200-11 반환")
    fun t27() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        signUp("user1")
        val user1Id = findUserId("user1")

        requestBan(user1Id, adminToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-11"))
            .andExpect(jsonPath("$.msg").value("회원이 영구 정지되었습니다."))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.startAt").isNotEmpty())
            .andExpect(jsonPath("$.data.userStatus").value("BANNED"))

        em.flush()
        em.clear()

        val user = userRepository.findById(user1Id).orElseThrow()
        assertThat(user.userStatus).isEqualTo(UserStatus.BANNED)

        val history = userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow()
        assertThat(history.status).isEqualTo(UserStatus.BANNED)
        assertThat(history.adminId).isEqualTo(adminId)
        assertThat(history.reason).isEqualTo("욕설")
        assertThat(history.endAt).isNull()
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 이미 영구 정지된 회원을 정지 요청 시 400-8 반환")
    fun t28() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        // 첫 번째 정지
        requestBan(user1Id, adminToken)
            .andExpect(status().isOk())

        // 두 번째 정지
        requestBan(user1Id, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-8"))
            .andExpect(jsonPath("$.msg").value("이미 영구 정지 상태인 계정입니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 관리자 계정을 정지 요청 시 400-6 반환")
    fun t29() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        requestBan(adminId, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-6"))
            .andExpect(jsonPath("$.msg").value("관리자의 계정을 정지시킬 수 없습니다."))

        // 상태 유지 확인
        em.flush()
        em.clear()

        val admin = userRepository.findById(adminId).orElseThrow()
        assertThat(admin.userStatus).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 존재하지 않는 회원을 정지 요청 시 404-1 반환")
    fun t30() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        requestBan(Long.MAX_VALUE, adminToken)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 로그인 없이 요청 시 401-113 반환")
    fun t31() {
        signUp("user1")
        val user1Id = findUserId("user1")

        requestBan(user1Id, null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 일반 회원이 요청 시 403-1 반환")
    fun t32() {
        signUp("user1")
        val user1Id = findUserId("user1")
        val user2Token = signUp("user2")

        requestBan(user1Id, user2Token)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 관리자의 기간 정지 회원에 대한 정지 해제 요청 시 200-12 반환")
    fun t33() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        signUp("user1")
        val user1Id = findUserId("user1")

        // 기간 정지
        requestSuspension(user1Id, adminToken)
            .andExpect(status().isOk())

        // 정지 해제 요청
        requestStatusChange("release", user1Id, "그냥 그냥 그냥 그냥", adminToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-12"))
            .andExpect(jsonPath("$.msg").value("회원의 정지가 해제되었습니다."))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))

        em.flush()
        em.clear()

        val user = userRepository.findById(user1Id).orElseThrow()
        assertThat(user.userStatus).isEqualTo(UserStatus.ACTIVE)

        val history = userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow()
        assertThat(history.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(history.adminId).isEqualTo(adminId)
        assertThat(history.reason).isEqualTo("그냥 그냥 그냥 그냥")
        assertThat(history.endAt).isNull()
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 관리자의 영구 정지 회원에 대한 정지 해제 요청 시 200-12 반환")
    fun t34() {
        createTestAdmin()
        val adminToken = loginAsAdmin()
        val adminId = findUserId(ADMIN_USERNAME)

        signUp("user1")
        val user1Id = findUserId("user1")

        // 영구 정지
        requestBan(user1Id, adminToken)
            .andExpect(status().isOk())

        // 정지 해제 요청
        requestStatusChange("release", user1Id, "진짜 그냥 그냥 그냥 그냥", adminToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-12"))
            .andExpect(jsonPath("$.msg").value("회원의 정지가 해제되었습니다."))
            .andExpect(jsonPath("$.data.username").value("user1"))
            .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))

        em.flush()
        em.clear()

        val user = userRepository.findById(user1Id).orElseThrow()
        assertThat(user.userStatus).isEqualTo(UserStatus.ACTIVE)

        val history = userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow()
        assertThat(history.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(history.adminId).isEqualTo(adminId)
        assertThat(history.reason).isEqualTo("진짜 그냥 그냥 그냥 그냥")
        assertThat(history.endAt).isNull()
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 활성 상태의 회원에 대한 정지 해제 요청 시 400-8 반환")
    fun t35() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        requestRelease(user1Id, adminToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-8"))
            .andExpect(jsonPath("$.msg").value("이미 활성 상태인 계정입니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 존재하지 않는 회원을 정지 해제 요청 시 404-1 반환")
    fun t36() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        requestRelease(Long.MAX_VALUE, adminToken)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-1"))
            .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 로그인 없이 요청 시 401-113 반환")
    fun t37() {
        signUp("user1")
        val user1Id = findUserId("user1")

        requestRelease(user1Id, null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 영구 정지된 회원은 userStatus=BANNED, suspendEndAt=null, statusReason 반환")
    fun t38() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        signUp("user1")
        val user1Id = findUserId("user1")

        requestBan(user1Id, adminToken)
            .andExpect(status().isOk())

        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.data.id").value(user1Id))
            .andExpect(jsonPath("$.data.userStatus").value("BANNED"))
            .andExpect(jsonPath("$.data.suspendEndAt").value(nullValue()))
            .andExpect(jsonPath("$.data.statusReason").value("욕설"))
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 기간 정지 후 탈퇴한 회원도 정지 정보 반환")
    fun t39() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        val user1Token = signUp("user1")
        val user1Id = findUserId("user1")

        // 정지 먼저 (탈퇴 후에는 findById 로 못 찾아서 정지 요청이 404)
        requestSuspension(user1Id, adminToken)
            .andExpect(status().isOk())

        // 정지된 상태로 탈퇴
        mvc.perform(
            delete("/api/v1/users/profile")
                .cookie(user1Token)
        )
            .andExpect(status().isNoContent())

        mvc.perform(
            get("/api/v1/admin/users/{id}", user1Id)
                .cookie(adminToken)
        ).andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-1"))
            .andExpect(jsonPath("$.data.id").value(user1Id))
            .andExpect(jsonPath("$.data.deleted").value(true))
            .andExpect(jsonPath("$.data.deletedAt").exists())
            .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.suspendEndAt").isNotEmpty())
            .andExpect(jsonPath("$.data.statusReason").value("노쇼 3회 누적"))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - SUSPEND된 의뢰인 계정은 공고 등록 요청 시 403-31 반환")
    fun t40() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        val user1Token = signUp("user1", "CLIENT")
        val user1Id = findUserId("user1")

        // 정지
        requestSuspension(user1Id, adminToken)
            .andExpect(status().isOk())

        // 정지된 의뢰인이 공고 등록 시도
        requestWritePost(user1Token)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-31"))
            .andExpect(jsonPath("$.msg").value("공고 등록 권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - SUSPEND된 동행인 계정은 공고 지원 요청 시 403-31 반환")
    fun t41() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        val clientToken = signUp("user1", "CLIENT")
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")

        // 의뢰인이 공고 등록
        val postId = writeOpenPost(clientToken)

        // 동행인 프로필 등록 + 교육 이수 처리 -> 정지만 아니면 지원 가능한 상태
        createEscortProfile(escortToken)
        escortProfileRepository.verify(escortId, LocalDateTime.now())

        // 동행인 정지
        requestSuspension(escortId, adminToken)
            .andExpect(status().isOk())

        // 정지된 동행인이 공고 지원 시도
        mvc.perform(
            post("/api/v1/applications/{postId}", postId)
                .cookie(escortToken)
        ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-31"))
            .andExpect(jsonPath("$.msg").value("활성 상태의 회원만 지원할 수 있습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - BANNED된 의뢰인 계정은 공고 등록 요청 시 403-31 반환")
    fun t42() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        val user1Token = signUp("user1", "CLIENT")
        val user1Id = findUserId("user1")

        // 정지
        requestBan(user1Id, adminToken)
            .andExpect(status().isOk())

        // 정지된 의뢰인이 공고 등록 시도
        requestWritePost(user1Token)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-31"))
            .andExpect(jsonPath("$.msg").value("공고 등록 권한이 없습니다."))
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - BANNED된 동행인 계정은 공고 지원 요청 시 403-31 반환")
    fun t43() {
        createTestAdmin()
        val adminToken = loginAsAdmin()

        val clientToken = signUp("user1", "CLIENT")
        val escortToken = signUp("escort1", "ESCORT")
        val escortId = findUserId("escort1")

        // 의뢰인이 공고 등록
        val postId = writeOpenPost(clientToken)

        // 동행인 프로필 등록 + 교육 이수 처리 -> 정지만 아니면 지원 가능한 상태
        createEscortProfile(escortToken)
        escortProfileRepository.verify(escortId, LocalDateTime.now())

        // 동행인 정지
        requestBan(escortId, adminToken)
            .andExpect(status().isOk())

        // 정지된 동행인이 공고 지원 시도
        mvc.perform(
            post("/api/v1/applications/{postId}", postId)
                .cookie(escortToken)
        ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-31"))
            .andExpect(jsonPath("$.msg").value("활성 상태의 회원만 지원할 수 있습니다."))
    }

    companion object {
        private const val ADMIN_USERNAME = "adminTest"
        private const val ADMIN_PASSWORD = "adminTest"

        // 회원 정보 수정 요청 본문 (t10 ~ t13 공통)
        private const val UPDATE_BODY = """
            {
                "email": "updated@user.user",
                "name": "김춘자",
                "birthDate": "1995-03-15",
                "phoneNum": "010-1111-2222",
                "region": "경기도"
            }
            """
    }
}
