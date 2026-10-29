package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.global.crypto.PiiConverter
import com.back.nbe12142team06.global.crypto.PiiLocalDateConverter
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity
import com.back.nbe12142team06.global.exception.InvalidException
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(UserBlindIndexListener::class)
class User internal constructor() : BaseSoftDeleteTimeEntity() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    // 로그인 아이디
    @Column(nullable = false, unique = true)
    lateinit var username: String
        protected set

    // 로그인 비밀번호
    @Column(nullable = false)
    lateinit var password: String
        protected set

    // 회원 이메일
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 500)
    lateinit var email: String
        protected set

    // 회원 실명
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var name: String
        protected set

    // 회원 역할
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    lateinit var role: Role
        protected set

    // 회원 성별
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    lateinit var gender: Gender
        protected set

    // 회원 생년월일
    @Convert(converter = PiiLocalDateConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var birthDate: LocalDate
        protected set

    // 회원 전화번호
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var phoneNum: String
        protected set

    // 지역
    @Column(nullable = false, length = 50)
    lateinit var region: String
        protected set

    // 계정 활성, 일시 정지, 영구 정지 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var userStatus: UserStatus = UserStatus.ACTIVE
        protected set

    // 이메일 블라인드 인덱스 (중복 검사용 HMAC 값, 복호화 불가)
    @Column(unique = true, length = 64)
    var emailHash: String? = null
        protected set

    // 전화번호 블라인드 인덱스 (중복 검사용 HMAC 값, 복호화 불가)
    @Column(unique = true, length = 64)
    var phoneNumHash: String? = null
        protected set

    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    var version: Long? = null
        protected set

    // password 는 PasswordEncoder.encode() 결과(Spring Security 가 String? 로 선언)를 그대로 받으려고 nullable 로 받고 안에서 검사한다
    constructor(
        username: String,
        password: String?,
        email: String,
        name: String,
        role: Role,
        gender: Gender,
        birthDate: LocalDate,
        phoneNum: String,
        region: String,
    ) : this() {
        this.username = username
        this.password = requireNotNull(password) { "비밀번호는 필수입니다." }
        this.email = email
        this.name = name
        this.role = role
        this.gender = gender
        this.birthDate = birthDate
        this.phoneNum = phoneNum
        this.region = region
    }

    // 탈퇴 여부
    val isDeleted: Boolean
        get() = deletedAt != null

    // ACTIVE 계정 여부
    val isActive: Boolean
        get() = userStatus == UserStatus.ACTIVE

    // 회원 정보 수정
    fun updateUser(
        password: String?,  // PasswordEncoder.encode() 결과를 그대로 받는다
        email: String,
        name: String,
        birthDate: LocalDate,
        phoneNum: String,
        region: String,
    ) {
        this.password = requireNotNull(password) { "비밀번호는 필수입니다." }
        this.email = email
        this.name = name
        this.birthDate = birthDate
        this.phoneNum = phoneNum
        this.region = region
    }

    // 블라인드 인덱스 설정
    fun updateBlindIndex(emailHash: String?, phoneNumHash: String?) {
        this.emailHash = emailHash
        this.phoneNumHash = phoneNumHash
    }

    // 회원 탈퇴 시 (재가입할 수 있게 아이디·이메일·전화번호를 비식별 값으로 바꾸고 해시를 비운다)
    fun deleteUser() {
        deletedAt = LocalDateTime.now()
        username = "deleted_$id"
        email = "deleted_$id"
        phoneNum = "deleted_$id"
        emailHash = null
        phoneNumHash = null
    }

    // 기간 정지
    fun suspend(adminId: Long?, reason: String): UserStatusHistory {
        changeStatus(UserStatus.SUSPENDED)
        return UserStatusHistory.suspend(this, adminId, reason)
    }

    // 영구 정지
    fun ban(adminId: Long?, reason: String): UserStatusHistory {
        changeStatus(UserStatus.BANNED)
        return UserStatusHistory.ban(this, adminId, reason)
    }

    // 관리자의 정지 해제
    fun release(adminId: Long?, reason: String): UserStatusHistory {
        changeStatus(UserStatus.ACTIVE)
        return UserStatusHistory.release(this, adminId, reason)
    }

    // 정지 기간 만료 시 해제
    fun expire(): UserStatusHistory {
        if (userStatus == UserStatus.BANNED) {
            throw InvalidException(9, "영구 정지된 계정은 해제만 가능합니다.")
        }
        changeStatus(UserStatus.ACTIVE)
        return UserStatusHistory.expire(this)
    }

    // 상태 변경 (같은 상태로의 변경, 허용되지 않는 전이는 불가)
    private fun changeStatus(newStatus: UserStatus) {
        val prevStatus = userStatus

        if (prevStatus == newStatus) {
            throw InvalidException(8, "이미 ${prevStatus.description} 상태인 계정입니다.")
        }
        if (!prevStatus.canTransitionTo(newStatus)) {
            throw InvalidException(9, "영구 정지된 계정은 해제만 가능합니다.")
        }

        userStatus = newStatus
    }
}