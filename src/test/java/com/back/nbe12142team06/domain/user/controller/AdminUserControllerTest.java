package com.back.nbe12142team06.domain.user.controller;

import com.back.nbe12142team06.domain.auth.entity.RefreshToken;
import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.enums.UserStatus;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class AdminUserControllerTest {

    private static final String ADMIN_USERNAME = "adminTest";
    private static final String ADMIN_PASSWORD = "adminTest";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserStatusHistoryRepository userStatusHistoryRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // 정지 해제 요청
    private ResultActions requestRelease(Long userId, Cookie token) throws Exception {
        var request = post("/api/v1/admin/users/{userId}/release", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "reason": "관리자 실수 ㅈㅅㅈㅅ"
                    }
                    """);

        if (token != null) {
            request.cookie(token);
        }

        return mvc.perform(request).andDo(print());
    }

    // 기간 정지 요청
    private ResultActions requestSuspension(Long userId, Cookie token) throws Exception {
        var request = post("/api/v1/admin/users/{userId}/suspension", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "reason": "노쇼 3회 누적"
                    }
                    """);

        if (token != null) {
            request.cookie(token);
        }

        return mvc.perform(request).andDo(print());
    }

    // 영구 정지 요청
    private ResultActions requestBan(Long userId, Cookie token) throws Exception {
        var request = post("/api/v1/admin/users/{userId}/ban", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "reason": "욕설"
                    }
                    """);

        if (token != null) {
            request.cookie(token);
        }

        return mvc.perform(request).andDo(print());
    }
    // 관리자 계정 주입
    private void createTestAdmin() {
        User admin = new User(
                ADMIN_USERNAME,
                passwordEncoder.encode(ADMIN_PASSWORD),
                "admin@admin.admin",
                "관리자",
                Role.ADMIN,
                Gender.MALE,
                LocalDate.of(2001, 1, 1),
                "010-9898-9898",
                "서울시"
        );
        userRepository.save(admin);
    }

    // 로그인 후 accessToken 쿠키 반환
    private Cookie login(String username, String password) throws Exception {
        String loginBody = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);

        Cookie accessToken = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        assertThat(accessToken).isNotNull();
        return accessToken;
    }

    // 관리자 로그인 (createTestAdmin() 호출 후 사용)
    private Cookie loginAsAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    // 역할 지정 회원가입 후 accessToken 쿠키 반환
    private Cookie signUp(String username, String role) throws Exception {
        String signUpBody = """
                {
                    "username": "%s",
                    "password": "testPassword",
                    "email": "%s@user.user",
                    "name": "김춘식",
                    "role": "%s",
                    "gender": "MALE",
                    "birthDate": "1990-05-20",
                    "phoneNum": "%s010-8080-0000",
                    "region": "서울시"
                }
                """.formatted(username, username, role, username);

        Cookie accessToken = mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        assertThat(accessToken).isNotNull();
        return accessToken;
    }

    // 동행 매니저 프로필 생성
    private void createEscortProfile(Cookie escortToken) throws Exception {
        mvc.perform(post("/api/v1/users/profile/escort")
                        .cookie(escortToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "intro": "동행 매니저입니다.",
                                    "bankName": "오픈은행",
                                    "accountHolder": "김춘식",
                                    "accountNumber": "123-0000000-123"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    // 일반 회원 가입 후 accessToken 쿠키 반환 (가입 시 토큰이 발급됨)
    private Cookie signUp(String username) throws Exception {
        String signUpBody = """
                {
                    "username": "%s",
                    "password": "testPassword",
                    "email": "%s@user.user",
                    "name": "김춘식",
                    "role": "CLIENT",
                    "gender": "MALE",
                    "birthDate": "1990-05-20",
                    "phoneNum": "%s010-8080-0000",
                    "region": "서울시"
                }
                """.formatted(username, username, username);

        Cookie accessToken = mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        assertThat(accessToken).isNotNull();
        return accessToken;
    }

    private Long findUserId(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 관리자가 정상적으로 단건 조회 시 200-1 반환")
    void t1() throws Exception {
        createTestAdmin();
        signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie adminToken = loginAsAdmin();

        // 회원 단건 조회
        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
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
                .andExpect(jsonPath("$.data.phoneNum").value("user1010-8080-0000"))
                .andExpect(jsonPath("$.data.region").value("서울시"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.suspendEndAt").value(nullValue()));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 탈퇴한 회원도 관리자는 조회할 수 있고 200-1 반환")
    void t2() throws Exception {
        createTestAdmin();
        Cookie user1Token = signUp("user1");
        Long user1Id = findUserId("user1");   // 탈퇴 후에는 조회되지 않으므로 먼저 확보

        // user1 탈퇴
        mvc.perform(delete("/api/v1/users/profile")
                        .cookie(user1Token))
                .andExpect(status().isNoContent());

        Cookie adminToken = loginAsAdmin();

        // 탈퇴 회원 단건 조회
        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.id").value(user1Id))
                .andExpect(jsonPath("$.data.username").value("deleted_%d".formatted(user1Id)))
                .andExpect(jsonPath("$.data.deletedAt").exists());
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 로그인 없이 조회 시 401-113 반환")
    void t3() throws Exception {
        createTestAdmin();
        signUp("user1");
        Long user1Id = findUserId("user1");

        // 쿠키 없이 조회
        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 일반 회원이 조회 시 403-1 반환")
    void t4() throws Exception {
        Cookie user1Token = signUp("user1");
        Long user1Id = findUserId("user1");

        // 일반 회원 토큰으로 조회 (자기 자신이어도 관리자 API는 불가)
        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
                        .cookie(user1Token)
        ).andDo(print());

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 존재하지 않는 회원 조회 시 404 반환")
    void t5() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        // 존재하지 않는 id로 조회
        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", Long.MAX_VALUE)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."));
    }


    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 관리자가 정상 조회 시 200 반환")
    void t6() throws Exception {
        // 관리자 로그인
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        // 다수의 회원 생성
        for (int i = 1; i < 20; i++){
            signUp("user%d".formatted(i));
        }

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users")
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-2"))
                .andExpect(jsonPath("$.msg").value("회원 목록 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(20))   // 관리자 1 + 회원 19
                .andExpect(jsonPath("$.data.totalPages").value(2))
                // 최신 가입자가 맨 앞
                .andExpect(jsonPath("$.data.content[0].username").value("user19"))
                .andExpect(jsonPath("$.data.content[9].username").value("user10"));

        // 생성일 내림차순 검증
        String responseBody = resultActions.andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        List<LocalDateTime> createdAts = JsonPath.<List<String>>read(responseBody, "$.data.content[*].createdAt")
                .stream()
                .map(LocalDateTime::parse)
                .toList();

        assertThat(createdAts).isSortedAccordingTo(Comparator.reverseOrder());

    }


    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 음수 페이지 요청 시 400-1 반환")
    void t7() throws Exception {
        // 관리자 로그인
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        // 다수의 회원 생성
        for (int i = 1; i < 4; i++){
            signUp("user%d".formatted(i));
        }

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users")
                        .param("page", "-1")
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-4"))
                .andExpect(jsonPath("$.msg").value("페이지 번호는 음수일 수 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 로그인 없이 요청 시 401-113 반환")
    void t8() throws Exception {

        // 다수의 회원 생성
        for (int i = 1; i < 4; i++){
            signUp("user%d".formatted(i));
        }

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users")
                        .param("page", "-1")
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 다건 조회 - 일반 회원이 조회 시 403-1 반환")
    void t9() throws Exception {

        Cookie user1Token = signUp("user1");

        // 다수의 회원 생성
        for (int i = 2; i < 4; i++){
            signUp("user%d".formatted(i));
        }

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users")
                        .param("page", "1")
                        .cookie(user1Token)
        ).andDo(print());

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 관리자가 정상 수정 시 200-3 반환")
    void t10() throws Exception {
        createTestAdmin();
        signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie adminToken = loginAsAdmin();

        String updateBody = """
            {
                "email": "updated@user.user",
                "name": "김춘자",
                "birthDate": "1995-03-15",
                "phoneNum": "010-1111-2222",
                "region": "경기도"
            }
            """;

        // 회원 정보 수정
        ResultActions resultActions = mvc.perform(
                patch("/api/v1/admin/users/{id}", user1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
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
                .andExpect(jsonPath("$.data.password").doesNotExist());

        // 비밀번호 유지 확인 (기존 비밀번호로 로그인 성공)
        login("user1", "testPassword");
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 탈퇴한 회원 수정 시 400-2 반환")
    void t11() throws Exception {
        createTestAdmin();
        Cookie userToken = signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie adminToken = loginAsAdmin();

        String updateBody = """
            {
                "email": "updated@user.user",
                "name": "김춘자",
                "birthDate": "1995-03-15",
                "phoneNum": "010-1111-2222",
                "region": "경기도"
            }
            """;

        // 회원 탈퇴
        mvc.perform(
                delete("/api/v1/users/profile")
                        .cookie(userToken)
                )
                .andExpect(status().isNoContent());

        // 회원 정보 수정
        ResultActions resultActions = mvc.perform(
                patch("/api/v1/admin/users/{id}", user1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-2"))
                .andExpect(jsonPath("$.msg").value("탈퇴한 회원의 정보는 수정할 수 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 로그인 없이 수정 시 401-113 반환")
    void t12() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");

        String updateBody = """
        {
            "email": "updated@user.user",
            "name": "김춘자",
            "birthDate": "1995-03-15",
            "phoneNum": "010-1111-2222",
            "region": "경기도"
        }
        """;

        // 쿠키 없이 수정
        ResultActions resultActions = mvc.perform(
                patch("/api/v1/admin/users/{id}", user1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 수정 - 일반 회원이 수정 시 403-1 반환")
    void t13() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie user2Token = signUp("user2");

        String updateBody = """
    {
        "email": "updated@user.user",
        "name": "김춘자",
        "birthDate": "1995-03-15",
        "phoneNum": "010-1111-2222",
        "region": "경기도"
    }
    """;

        // 일반 회원 토큰으로 다른 회원 수정
        ResultActions resultActions = mvc.perform(
                patch("/api/v1/admin/users/{id}", user1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .cookie(user2Token)
        ).andDo(print());

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("권한이 없습니다."));
    }


    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 관리자의 정상 탈퇴 요청 시 200-4 반환")
    void t14() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");

        ResultActions resultActions = mvc.perform(
                        delete("/api/v1/admin/users/{id}", user1Id)
                                .cookie(adminToken)
                )
                .andDo(print());


        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-4"))
                .andExpect(jsonPath("$.msg").value("회원 탈퇴가 완료되었습니다."));

        em.flush();
        em.clear();

        User deletedUser = this.userRepository.findByIdIncludingDeleted(user1Id).orElseThrow();

        assertThat(deletedUser.getEmail()).isEqualTo("deleted_%d".formatted(user1Id));
        assertThat(deletedUser.getUsername()).isEqualTo("deleted_%d".formatted(user1Id));
        assertThat(deletedUser.getPhoneNum()).isEqualTo("deleted_%d".formatted(user1Id));
        assertThat(deletedUser.getDeletedAt()).isNotNull();

        List<RefreshToken> refreshTokenList = this.refreshTokenRepository.findAllByUserId(user1Id);

        assertThat(refreshTokenList).isNotEmpty();

        for  (RefreshToken refreshToken : refreshTokenList) {
            assertThat(refreshToken.isRevoked()).isTrue();
        }
    }

    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 존재하지 않는 회원 탈퇴 요청 시 404 반환")
    void t15() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();


        ResultActions resultActions = mvc.perform(
                        delete("/api/v1/admin/users/{id}", Long.MAX_VALUE)
                                .cookie(adminToken)
                )
                .andDo(print());


        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."));
    }


    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 관리자가 자신의 계정에 대한 탈퇴 요청 시 400-3")
    void t16() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId("adminTest");

        ResultActions resultActions = mvc.perform(
                        delete("/api/v1/admin/users/{id}", adminId)
                                .cookie(adminToken)
                )
                .andDo(print());


        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-3"))
                .andExpect(jsonPath("$.msg").value("관리자는 자신의 계정을 탈퇴시킬 수 없습니다."));
    }


    @Test
    @DisplayName("[AdminUserController] 회원 정보 탈퇴 - 이미 탈퇴한 회원에 대한 탈퇴 요청 시 400-4 반환")
    void t17() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");

        // 정상 탈퇴
        ResultActions resultActions = mvc.perform(
                        delete("/api/v1/admin/users/{id}", user1Id)
                                .cookie(adminToken)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-4"))
                .andExpect(jsonPath("$.msg").value("회원 탈퇴가 완료되었습니다."));

        // 또또 탈퇴 요청
        ResultActions resultActions2 = mvc.perform(
                        delete("/api/v1/admin/users/{id}", user1Id)
                                .cookie(adminToken)
                )
                .andDo(print());

        resultActions2
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-5"))
                .andExpect(jsonPath("$.msg").value("이미 탈퇴한 회원입니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 동행 매니저 프로필 타인 조회 - 관리자 조회 시 계좌 정보까지 200-9 반환")
    void t18() throws Exception {
        Cookie escortToken = signUp("escort1", "ESCORT");
        Long escortId = findUserId("escort1");
        createEscortProfile(escortToken);

        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        em.flush();
        em.clear();

        ResultActions resultActions = mvc.perform(
                        get("/api/v1/admin/users/{userId}/profile/escort", escortId)
                                .cookie(adminToken))
                .andDo(print());

        resultActions
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
                .andExpect(jsonPath("$.data.accountNumber").value("123-0000000-123"));
    }
    @Test
    @DisplayName("[AdminUserController] 동행 매니저 프로필 타인 조회 - 프로필이 없으면 404 반환")
    void t19() throws Exception {
        Cookie escortToken = signUp("escort1", "ESCORT");
        Long escortId = findUserId("escort1");

        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        em.flush();
        em.clear();

        ResultActions resultActions = mvc.perform(
                        get("/api/v1/admin/users/{userId}/profile/escort", escortId)
                                .cookie(adminToken))
                .andDo(print());

        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-3"))
                .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 관리자가 ACTIVE 상태의 유저를 정지 요청 시 유저 상태를 SUSPENDED로 변경 후 200-10 반환")
    void t20() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        signUp("user1");
        Long user1Id = findUserId("user1");

        ResultActions resultActions = mvc.perform(
                post("/api/v1/admin/users/{userId}/suspension",  user1Id)
                        .cookie(adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "노쇼 3회 누적"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-10"))
                .andExpect(jsonPath("$.msg").value("회원이 기간 정지되었습니다."))
                .andExpect(jsonPath("$.data.username").value("user1"))
                .andExpect(jsonPath("$.data.startAt").isNotEmpty())
                .andExpect(jsonPath("$.data.endAt").isNotEmpty())
                .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"));

        em.flush();
        em.clear();

        User user = this.userRepository.findById(user1Id).orElseThrow();
        assertThat(user.getUserStatus()).isEqualTo(UserStatus.SUSPENDED);

        UserStatusHistory history = this.userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow();
        assertThat(history.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(history.getAdminId()).isEqualTo(adminId);
        assertThat(history.getReason()).isEqualTo("노쇼 3회 누적");
        assertThat(history.getEndAt()).isNotNull();
        assertThat(history.getEndAt().getMinute()).isZero();
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 이미 기간 정지된 회원을 정지 요청 시 400-8 반환")
    void t21() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");

        // 첫 번째 정지
        requestSuspension(user1Id, adminToken)
                .andExpect(status().isOk());

        // 두 번째 정지
        requestSuspension(user1Id, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-8"))
                .andExpect(jsonPath("$.msg").value("이미 기간 정지 상태인 계정입니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 영구 정지된 회원을 정지 요청 시 400-9 반환")
    void t22() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        signUp("user1");
        Long user1Id = findUserId("user1");

        User user = userRepository.findById(user1Id).orElseThrow();
        userStatusHistoryRepository.save(user.ban(adminId, "영구 정지 테스트"));
        em.flush();
        em.clear();

        requestSuspension(user1Id, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-9"))
                .andExpect(jsonPath("$.msg").value("영구 정지된 계정은 해제만 가능합니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 관리자 계정을 정지 요청 시 400-6 반환")
    void t23() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        requestSuspension(adminId, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-6"))
                .andExpect(jsonPath("$.msg").value("관리자의 계정을 정지시킬 수 없습니다."));

        // 상태 유지 확인
        em.flush();
        em.clear();

        User admin = userRepository.findById(adminId).orElseThrow();
        assertThat(admin.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 존재하지 않는 회원을 정지 요청 시 404-1 반환")
    void t24() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        requestSuspension(Long.MAX_VALUE, adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 로그인 없이 요청 시 401-113 반환")
    void t25() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");

        requestSuspension(user1Id, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("[AdminUserController] 기간 정지 요청 - 일반 회원이 요청 시 403-1 반환")
    void t26() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie user2Token = signUp("user2");

        requestSuspension(user1Id, user2Token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 관리자가 ACTIVE 상태의 유저를 정지 요청 시 유저 상태를 BANNED로 변경 후 200-11 반환")
    void t27() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        signUp("user1");
        Long user1Id = findUserId("user1");

        ResultActions resultActions = mvc.perform(
                post("/api/v1/admin/users/{userId}/ban",  user1Id)
                        .cookie(adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "욕설"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-11"))
                .andExpect(jsonPath("$.msg").value("회원이 영구 정지되었습니다."))
                .andExpect(jsonPath("$.data.username").value("user1"))
                .andExpect(jsonPath("$.data.startAt").isNotEmpty())
                .andExpect(jsonPath("$.data.userStatus").value("BANNED"));

        em.flush();
        em.clear();

        User user = this.userRepository.findById(user1Id).orElseThrow();
        assertThat(user.getUserStatus()).isEqualTo(UserStatus.BANNED);

        UserStatusHistory history = this.userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow();
        assertThat(history.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(history.getAdminId()).isEqualTo(adminId);
        assertThat(history.getReason()).isEqualTo("욕설");
        assertThat(history.getEndAt()).isNull();
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 이미 영구 정지된 회원을 정지 요청 시 400-8 반환")
    void t28() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");

        // 첫 번째 정지
        requestBan(user1Id, adminToken)
                .andExpect(status().isOk());

        // 두 번째 정지
        requestBan(user1Id, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-8"))
                .andExpect(jsonPath("$.msg").value("이미 영구 정지 상태인 계정입니다."));
    }
    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 관리자 계정을 정지 요청 시 400-6 반환")
    void t29() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        requestBan(adminId, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-6"))
                .andExpect(jsonPath("$.msg").value("관리자의 계정을 정지시킬 수 없습니다."));

        // 상태 유지 확인
        em.flush();
        em.clear();

        User admin = userRepository.findById(adminId).orElseThrow();
        assertThat(admin.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 존재하지 않는 회원을 정지 요청 시 404-1 반환")
    void t30() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        requestBan(Long.MAX_VALUE, adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."));
    }
    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 로그인 없이 요청 시 401-113 반환")
    void t31() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");

        requestBan(user1Id, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }
    @Test
    @DisplayName("[AdminUserController] 영구 정지 요청 - 일반 회원이 요청 시 403-1 반환")
    void t32() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");
        Cookie user2Token = signUp("user2");

        requestBan(user1Id, user2Token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 관리자의 기간 정지 회원에 대한 정지 해제 요청 시 200-12 반환")
    void t33() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        signUp("user1");
        Long user1Id = findUserId("user1");

        // 기간 정지
        requestSuspension(user1Id, adminToken)
                .andExpect(status().isOk());

        // 정지 해제 요청
        ResultActions resultActions = mvc.perform(
                post("/api/v1/admin/users/{userId}/release", user1Id)
                        .cookie(adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "그냥 그냥 그냥 그냥"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-12"))
                .andExpect(jsonPath("$.msg").value("회원의 정지가 해제되었습니다."))
                .andExpect(jsonPath("$.data.username").value("user1"))
                .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"));

        em.flush();
        em.clear();

        User user = this.userRepository.findById(user1Id).orElseThrow();
        assertThat(user.getUserStatus()).isEqualTo(UserStatus.ACTIVE);

        UserStatusHistory history = this.userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow();
        assertThat(history.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(history.getAdminId()).isEqualTo(adminId);
        assertThat(history.getReason()).isEqualTo("그냥 그냥 그냥 그냥");
        assertThat(history.getEndAt()).isNull();
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 관리자의 영구 정지 회원에 대한 정지 해제 요청 시 200-12 반환")
    void t34() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();
        Long adminId = findUserId(ADMIN_USERNAME);

        signUp("user1");
        Long user1Id = findUserId("user1");

        // 영구 정지
        requestBan(user1Id, adminToken)
                .andExpect(status().isOk());

        // 정지 해제 요청
        ResultActions resultActions = mvc.perform(
                post("/api/v1/admin/users/{userId}/release", user1Id)
                        .cookie(adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "진짜 그냥 그냥 그냥 그냥"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-12"))
                .andExpect(jsonPath("$.msg").value("회원의 정지가 해제되었습니다."))
                .andExpect(jsonPath("$.data.username").value("user1"))
                .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"));

        em.flush();
        em.clear();

        User user = this.userRepository.findById(user1Id).orElseThrow();
        assertThat(user.getUserStatus()).isEqualTo(UserStatus.ACTIVE);

        UserStatusHistory history = this.userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElseThrow();
        assertThat(history.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(history.getAdminId()).isEqualTo(adminId);
        assertThat(history.getReason()).isEqualTo("진짜 그냥 그냥 그냥 그냥");
        assertThat(history.getEndAt()).isNull();
    }


    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 활성 상태의 회원에 대한 정지 해제 요청 시 400-8 반환")
    void t35() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");


        requestRelease(user1Id, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-8"))
                .andExpect(jsonPath("$.msg").value("이미 활성 상태인 계정입니다."));

    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 존재하지 않는 회원을 정지 해제 요청 시 404-1 반환")
    void t36() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        requestRelease(Long.MAX_VALUE, adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("회원 정보를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("[AdminUserController] 정지 해제 요청 - 로그인 없이 요청 시 401-113 반환")
    void t37() throws Exception {
        signUp("user1");
        Long user1Id = findUserId("user1");

        requestRelease(user1Id, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value("401-113"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 영구 정지된 회원은 userStatus=BANNED, suspendEndAt=null, statusReason 반환")
    void t38() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        signUp("user1");
        Long user1Id = findUserId("user1");

        requestBan(user1Id, adminToken)
                .andExpect(status().isOk());

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-1"))
                .andExpect(jsonPath("$.data.id").value(user1Id))
                .andExpect(jsonPath("$.data.userStatus").value("BANNED"))
                .andExpect(jsonPath("$.data.suspendEndAt").value(nullValue()))
                .andExpect(jsonPath("$.data.statusReason").value("욕설"));
    }

    @Test
    @DisplayName("[AdminUserController] 회원 단건 조회 - 기간 정지 후 탈퇴한 회원도 정지 정보 반환")
    void t39() throws Exception {
        createTestAdmin();
        Cookie adminToken = loginAsAdmin();

        Cookie user1Token = signUp("user1");
        Long user1Id = findUserId("user1");

        // 정지 먼저 (탈퇴 후에는 findById 로 못 찾아서 정지 요청이 404)
        requestSuspension(user1Id, adminToken)
                .andExpect(status().isOk());

        // 정지된 상태로 탈퇴
        mvc.perform(delete("/api/v1/users/profile")
                        .cookie(user1Token))
                .andExpect(status().isNoContent());

        ResultActions resultActions = mvc.perform(
                get("/api/v1/admin/users/{id}", user1Id)
                        .cookie(adminToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-1"))
                .andExpect(jsonPath("$.data.id").value(user1Id))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.deletedAt").exists())
                .andExpect(jsonPath("$.data.userStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.suspendEndAt").isNotEmpty())
                .andExpect(jsonPath("$.data.statusReason").value("노쇼 3회 누적"));
    }
}