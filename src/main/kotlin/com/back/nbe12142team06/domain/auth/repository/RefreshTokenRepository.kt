package com.back.nbe12142team06.domain.auth.repository

import com.back.nbe12142team06.domain.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    // 토큰의 해시값으로 조회
    fun findByTokenHash(tokenHash: String): RefreshToken?

    // 회원 탈퇴 시 아직 폐기되지 않은 토큰들 조회
    fun findAllByUserIdAndRevokedAtIsNull(userId: Long): List<RefreshToken>

    fun findAllByUserId(userId: Long): List<RefreshToken>
}