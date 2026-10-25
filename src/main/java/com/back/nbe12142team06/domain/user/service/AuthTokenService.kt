package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.security.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AuthTokenService(
    @param:Value("\${custom.jwt.secret-key}") private val secretPattern: String,
    @param:Value("\${custom.jwt.expire-seconds}") private val expireSeconds: Long,
) {

    // 회원 정보(id, username, role)를 담은 access token 발급
    fun genAccessToken(user: User): String =
        JwtProvider.toString(
            secretPattern,
            expireSeconds,
            mapOf(
                "id" to checkNotNull(user.id) { "저장되지 않은 회원은 토큰을 발급할 수 없습니다." },
                "username" to user.username,
                "role" to user.role.name,
            ),
        )

    // 토큰 검증 후 필요한 값만 꺼내서 반환 (id 는 Integer 로 파싱될 수 있어 Long 으로 통일)
    fun payload(jwt: String): Map<String, Any> {
        val payload = JwtProvider.payload(jwt, secretPattern)

        return mapOf(
            "id" to (payload["id"] as Number).toLong(),
            "username" to payload["username"] as String,
            "role" to payload["role"] as String,
        )
    }
}