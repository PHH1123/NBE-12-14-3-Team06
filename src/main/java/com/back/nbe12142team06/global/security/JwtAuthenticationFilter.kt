package com.back.nbe12142team06.global.security

import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.rq.Rq
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val userService: UserService,
    private val rq: Rq,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // /api, /ws 로 시작하는 요청만 인증 시도 (웹소켓 연결 - 시큐리티에 인증 정보 채우기)
        val uri = request.requestURI
        if (uri.startsWith("/api/") || uri.startsWith("/ws/")) {
            authenticate(request)
        }

        // 인증이 안되었더라도 통과, 어차피 인가 단계에서 잡히고 /auth/refresh 호출을 위해서입니다.
        filterChain.doFilter(request, response)
    }

    private fun authenticate(request: HttpServletRequest) {
        // 토큰 없으면 익명 상태로 통과
        val accessToken = rq.accessToken.ifBlank { return }

        try {
            setAuthentication(userService.payload(accessToken))
        } catch (e: ExpiredJwtException) {
            request.setAttribute(AUTH_ERROR, ERROR_EXPIRED) // 토큰이 만료인 경우 (api/v1/auth/refresh로 재발급)
        } catch (e: JwtException) {
            request.setAttribute(AUTH_ERROR, ERROR_INVALID) // 재로그인이 필요한 경우
        } catch (e: IllegalArgumentException) {
            request.setAttribute(AUTH_ERROR, ERROR_INVALID)
        }
    }

    // payload에서 꺼낸 값으로 인증 객체 생성 후 SecurityContext에 저장
    private fun setAuthentication(payload: Map<String, Any>) {
        val id = (payload["id"] as Number).toLong()
        val username = payload["username"] as String
        val role = Role.valueOf(payload["role"] as String)

        // Role에 맞는 권한 설정
        val authorities = role.toAuthorities()
        val principal = SecurityUser(id, username, authorities)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, authorities)
    }

    companion object {
        const val AUTH_ERROR = "authError"
        const val ERROR_EXPIRED = "expired"
        const val ERROR_INVALID = "invalid_token"
    }
}