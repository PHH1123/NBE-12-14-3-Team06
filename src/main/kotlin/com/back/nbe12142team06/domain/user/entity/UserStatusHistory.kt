package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.domain.user.enums.UserStatus
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "user_status_history")
class UserStatusHistory internal constructor() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 대상 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    // 계정 상태를 변경한 관리자 ID (회원가입, 기간 만료 해제는 null)
    @Column(name = "admin_id")
    var adminId: Long? = null
        protected set

    // 바뀐 계정 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    lateinit var status: UserStatus
        protected set

    // 변경 이유
    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var reason: String
        protected set

    // SUSPENDED 일때 정지가 풀리는 시각
    var endAt: LocalDateTime? = null
        protected set

    // 이력 생성 시간
    @CreatedDate
    @Column(nullable = false, updatable = false, name = "created_at")
    var createdAt: LocalDateTime? = null
        protected set

    // 생성은 아래 정적 팩토리로만 한다
    private constructor(
        user: User,
        status: UserStatus,
        reason: String,
        adminId: Long? = null,
        endAt: LocalDateTime? = null,
    ) : this() {
        this.user = user
        this.status = status
        this.reason = reason
        this.adminId = adminId
        this.endAt = endAt
    }

    companion object {
        private const val SUSPENSION_DAYS = 7L

        // 회원가입
        fun signup(user: User) =
            UserStatusHistory(user, UserStatus.ACTIVE, "회원가입")

        // 기간 정지 (정지 해제 시각은 정각 단위로 맞춘다)
        fun suspend(user: User, adminId: Long?, reason: String) =
            UserStatusHistory(
                user, UserStatus.SUSPENDED, reason, adminId,
                endAt = LocalDateTime.now().plusDays(SUSPENSION_DAYS).truncatedTo(ChronoUnit.HOURS),
            )

        // 영구 정지
        fun ban(user: User, adminId: Long?, reason: String) =
            UserStatusHistory(user, UserStatus.BANNED, reason, adminId)

        // 관리자 해제
        fun release(user: User, adminId: Long?, reason: String) =
            UserStatusHistory(user, UserStatus.ACTIVE, reason, adminId)

        // 기간 정지 기간 만료 시 해제
        fun expire(user: User) =
            UserStatusHistory(user, UserStatus.ACTIVE, "기간 정지 만료")
    }
}