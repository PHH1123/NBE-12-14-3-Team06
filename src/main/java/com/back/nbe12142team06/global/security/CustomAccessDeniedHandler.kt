package com.back.nbe12142team06.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class CustomAccessDeniedHandler : AccessDeniedHandler {

    // 인증은 됐지만 권한이 부족한 경우 호출 (403)
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json; charset=UTF-8"
        response.writer.write(
            """
            {
                "statusCode": "403-1",
                "msg": "권한이 없습니다."
            }
            """.trimIndent()
        )
    }
}