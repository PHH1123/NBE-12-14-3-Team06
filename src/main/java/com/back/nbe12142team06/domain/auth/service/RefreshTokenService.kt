package com.back.nbe12142team06.domain.auth.service

import com.back.nbe12142team06.domain.auth.entity.RefreshToken
import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.exception.UnauthorizedException
import com.back.nbe12142team06.global.security.RefreshTokenGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class RefreshTokenService(
    private val userService: UserService,
    private val refreshTokenRepository: RefreshTokenRepository,
    @param:Value("\${custom.jwt.refresh-expire-seconds}") private val refreshExpireSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 리프레시 토큰 생성, 해시값 저장, 원본 토큰 반환
    @Transactional
    fun generate(user: User): String {
        val rawToken = RefreshTokenGenerator.generate()
        val hash = RefreshTokenGenerator.hash(rawToken)

        refreshTokenRepository.save(RefreshToken.create(user, hash, Duration.ofSeconds(refreshExpireSeconds)))

        return rawToken
    }

    // 리프레시 토큰 유효성 검증 후 새로운 access token 반환
    @Transactional(readOnly = true)
    fun refresh(rawToken: String): String {
        // 빈 토큰 검사
        if (rawToken.isBlank()) {
            log.warn("[토큰 재발급 실패] 리프레시 토큰 없음")
            throw UnauthorizedException(91, "리프레시 토큰이 없습니다.")
        }

        // 유효성 검증
        val refreshToken = refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(rawToken))
            .orElseThrow {
                log.warn("[토큰 재발급 실패] 알 수 없는 리프레시 토큰")
                UnauthorizedException(92, "유효하지 않은 토큰입니다.")
            }

        // 폐기된 토큰인지 검증
        if (refreshToken.isRevoked) {
            log.warn("[토큰 재발급 실패] 폐기된 토큰 userId={}", refreshToken.user.id)
            throw UnauthorizedException(93, "유효하지 않은 토큰입니다.")
        }

        // 만료된 토큰인지 검증
        if (refreshToken.isExpired) {
            log.warn("[토큰 재발급 실패] 만료된 토큰 userId={}", refreshToken.user.id)
            throw UnauthorizedException(94, "만료된 토큰입니다. 다시 로그인해주세요.")
        }

        return userService.genAccessToken(refreshToken.user)
    }

    @Transactional
    fun revoke(rawToken: String) {
        // 빈 토큰 검사
        if (rawToken.isBlank()) return

        refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(rawToken))
            .ifPresent(RefreshToken::revoke)
    }
}