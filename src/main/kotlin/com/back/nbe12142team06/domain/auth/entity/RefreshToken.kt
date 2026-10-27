package com.back.nbe12142team06.domain.auth.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_token")
class RefreshToken internal constructor() : BaseTimeEntity() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @Column(nullable = false, unique = true, length = 64)
    lateinit var tokenHash: String
        protected set

    @Column(nullable = false)
    lateinit var expiresAt: LocalDateTime
        protected set

    // 이미 revoked_at이 null이 아닌 토큰이 날아오면 탈취 감지 가능하다합니다!
    var revokedAt: LocalDateTime? = null
        protected set

    // 폐기된 토큰인지 확인
    val isRevoked: Boolean
        get() = revokedAt != null

    // 토큰의 생존 시간이 만료되었는지 확인
    val isExpired: Boolean
        get() = expiresAt.isBefore(LocalDateTime.now())

    // 사용 가능한 토큰인지 확인
    val isUsable: Boolean
        get() = !isRevoked && !isExpired

    // 토큰 폐기 (이미 폐기된 토큰은 최초 폐기 시각 유지)
    fun revoke() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now()
        }
    }

    companion object {
        // now + ttl(Time to Live)를 만료 시각으로 생성
        fun create(user: User, tokenHash: String, ttl: Duration): RefreshToken =
            RefreshToken().apply {
                this.user = user
                this.tokenHash = tokenHash
                this.expiresAt = LocalDateTime.now().plus(ttl)
            }
    }
}