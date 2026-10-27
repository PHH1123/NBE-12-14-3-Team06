package com.back.nbe12142team06.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    // 인증이 필요한데 SecurityContext가 비어있을 때 호출된다
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val (resultCode, msg) = when (request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR)) {
            JwtAuthenticationFilter.ERROR_EXPIRED -> "401-111" to "만료된 토큰입니다."       // 만료인 경우
            JwtAuthenticationFilter.ERROR_INVALID -> "401-112" to "유효하지 않은 토큰입니다." // 서명 불일치, 형식 오류
            else -> "401-113" to "로그인 후 이용해주세요."                                  // 토큰이 없는 경우
        }

        write(response, HttpServletResponse.SC_UNAUTHORIZED, resultCode, msg)
    }

    private fun write(response: HttpServletResponse, status: Int, resultCode: String, msg: String) {
        response.status = status
        response.contentType = "application/json; charset=UTF-8"
        response.writer.write(
            """
            {
                "statusCode": "$resultCode",
                "msg": "$msg"
            }
            """.trimIndent()
        )
    }
}