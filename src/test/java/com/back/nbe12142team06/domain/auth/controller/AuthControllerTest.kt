package com.back.nbe12142team06.domain.auth.controller

import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository
import com.back.nbe12142team06.global.security.RefreshTokenGenerator
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    // 회원 가입 (username = user1, password = pwd1)
    private fun signUp(): ResultActions =
        mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_BODY)
        ).andDo(print())

    // 로그인
    private fun login(username: String, password: String): ResultActions =
        mvc.perform(
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
        ).andDo(print())

    @Test
    @DisplayName("[AuthController] 로그인 - 회원가입 한 아이디로 정상 로그인")
    fun t1() {
        signUp()

        login("user1", "pwd1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-92"))
            .andExpect(jsonPath("$.msg").value("김춘식님 반갑습니다."))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.name").value("김춘식"))
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().path("accessToken", "/"))  // access Token의 요청 Path가 전체인지 검증
            .andExpect(cookie().path("refreshToken", "/api/v1/auth"))  // refresh Token의 요청 Path가 /api/v1/auth/refresh인지 검증
    }

    @Test
    @DisplayName("[AuthController] 로그인 - 존재하지 않는 아이디로 로그인 시도 시 401")
    fun t2() {
        login("user1", "pwd1")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-1"))
            .andExpect(jsonPath("$.msg").value("아이디 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(cookie().doesNotExist("accessToken"))
            .andExpect(cookie().doesNotExist("refreshToken"))
    }

    @Test
    @DisplayName("[AuthController] 로그인 - 아이디는 존재하지만 비밀번호가 옳지 않은 로그인 시도 시 401")
    fun t3() {
        signUp()

        login("user1", "pwd999999999999999999999")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-2"))
            .andExpect(jsonPath("$.msg").value("아이디 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(cookie().doesNotExist("accessToken"))
            .andExpect(cookie().doesNotExist("refreshToken"))
    }

    @Test
    @DisplayName("[AuthController] 로그아웃 - 로그아웃 시 토큰 폐기")
    fun t4() {
        val signUpResponse = signUp().andReturn().response
        val accessToken = signUpResponse.getCookie("accessToken")!!
        val rawRefreshToken = signUpResponse.getCookie("refreshToken")!!

        mvc.perform(
            delete("/api/v1/auth/logout")
                .cookie(accessToken)
                .cookie(rawRefreshToken)
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-93"))
            .andExpect(jsonPath("$.msg").value("로그아웃 되었습니다."))
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

        // 캐시 비우고 실제로 DB에서 조회
        em.flush()
        em.clear()

        // DB에서 폐기 확인
        val hash = RefreshTokenGenerator.hash(rawRefreshToken.value)
        val saved = refreshTokenRepository.findByTokenHash(hash).orElseThrow()
        assertThat(saved.isRevoked).isTrue()
    }

    companion object {
        private val SIGN_UP_BODY = """
            {
                "username": "user1",
                "password": "pwd1",
                "email": "first@test.test",
                "name": "김춘식",
                "role": "CLIENT",
                "gender": "MALE",
                "birthDate": "1990-05-20",
                "phoneNum": "010-1234-5678",
                "region": "서울시"
            }
            """
    }
}