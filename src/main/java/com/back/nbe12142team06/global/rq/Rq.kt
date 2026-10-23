package com.back.nbe12142team06.global.rq

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

@Component
@RequestScope
class Rq(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
    @param:Value("\${custom.cookie.secure}") private val secure: Boolean,
    @param:Value("\${custom.cookie.same-site}") private val sameSite: String,
    @param:Value("\${custom.jwt.refresh-expire-seconds}") private val refreshExpireSeconds: Long,
) {
    val accessToken: String
        get() = getCookieValue(ACCESS_TOKEN)

    val refreshToken: String
        get() = getCookieValue(REFRESH_TOKEN)

    fun setAccessTokenCookie(token: String) =
        addCookie(ACCESS_TOKEN, token, "/", refreshExpireSeconds)

    // refresh 쿠키는 path를 재발급 경로로 제한해 일반 요청에 실리지 않게 한다
    fun setRefreshTokenCookie(token: String) =
        addCookie(REFRESH_TOKEN, token, REFRESH_PATH, refreshExpireSeconds)

    fun clearTokenCookies() {
        deleteCookie(ACCESS_TOKEN, "/")
        deleteCookie(REFRESH_TOKEN, REFRESH_PATH)
    }

    // 이름이 같고 값이 비어있지 않은 첫 쿠키를 반환, 없으면 기본 값 반환
    private fun getCookieValue(name: String, defaultValue: String = ""): String =
        request.cookies
            ?.firstOrNull { it.name == name && it.value.isNotBlank() }
            ?.value
            ?: defaultValue

    // 쿠키 생성 path로 전송 범위, maxAgeSeconds로 수명 제어
    private fun addCookie(name: String, value: String, path: String, maxAgeSeconds: Long) {
        val cookie = Cookie(name, value).apply {
            this.path = path
            maxAge = maxAgeSeconds.toInt()
            isHttpOnly = true // JS로 접근하는 것을 차단하여 XSS 방어
            secure = this@Rq.secure
            setAttribute("SameSite", sameSite)
        }
        response.addCookie(cookie)
    }

    // 쿠키 삭제 maxAge를 0으로 설정하여 즉시 만료되도록 쿠키를 삭제
    private fun deleteCookie(name: String, path: String) = addCookie(name, "", path, 0)

    companion object {
        private const val ACCESS_TOKEN = "accessToken"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val REFRESH_PATH = "/api/v1/auth"
    }
}
