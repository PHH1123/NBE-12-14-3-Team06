package com.back.nbe12142team06.global.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey

object JwtProvider {
    // 토큰 발급
    fun toString(
        secret: String,
        expireSeconds: Long,
        body: Map<String, Any>): String {

        val issuedAt = Date()
        val expiration = Date(issuedAt.time + expireSeconds * 1000L)

        return Jwts.builder()
            .claims(body)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(secretKey(secret))
            .compact()
    }


    // 검증 후 페이로드 추출
    fun payload(
        jwt: String,
        secretPattern: String
    ): Map<String, Any> =
        Jwts.parser()
            .verifyWith(secretKey(secretPattern))
            .build()
            .parseSignedClaims(jwt)
            .payload

    private fun secretKey(secret: String): SecretKey =
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
}



