package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.domain.user.enums.EscortGrade
import com.back.nbe12142team06.global.crypto.PiiConverter
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import java.time.LocalDateTime
import kotlin.math.roundToLong

@Entity
@Table(name = "escort_profiles")
class EscortProfile internal constructor() : BaseTimeEntity() {  // JPA용 기본 생성자

    // 회원 개인 키 ID (@MapsId 로 user 의 id 를 그대로 사용)
    @Id
    var userId: Long? = null
        protected set

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    lateinit var user: User
        protected set

    // 신원 인증 여부
    @Column(nullable = false)
    var verified: Boolean = false
        protected set

    // 신원 인증 날짜, 시각
    var verifiedAt: LocalDateTime? = null
        protected set

    // 자기소개
    @Column(nullable = false, length = 500)
    lateinit var intro: String
        protected set

    // 동행 완료 건수
    @Column(nullable = false)
    var completedCount: Int = 0
        protected set

    // 평점 합계
    @Column(nullable = false)
    var ratingSum: Int = 0
        protected set

    // 받은 평점 개수
    @Column(nullable = false)
    var ratingCount: Int = 0
        protected set

    // 노쇼 횟수
    @Column(nullable = false)
    var noShowCount: Int = 0
        protected set

    // 등급 (동행 완료 횟수와 노쇼 횟수로 계산해 저장한다)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SEED'")
    @Column(nullable = false, length = 20)
    var grade: EscortGrade = EscortGrade.SEED
        protected set

    // 은행 이름
    @Column(nullable = false, length = 20)
    lateinit var bankName: String
        protected set

    // 예금주명
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var accountHolder: String
        protected set

    // 계좌번호
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var accountNumber: String
        protected set

    // 낙관적 락 (벌크 UPDATE 쿼리에서도 version 을 함께 올린다)
    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    var version: Long? = null
        protected set

    constructor(
        user: User,
        intro: String,
        bankName: String,
        accountHolder: String,
        accountNumber: String,
    ) : this() {
        this.user = user
        this.intro = intro
        this.bankName = bankName
        this.accountHolder = accountHolder
        this.accountNumber = accountNumber
    }

    val requiredId : Long
        get() = userId ?: error("ID가 NULL입니다.")

    // 평균 평점 (소수점 첫째 자리 반올림, 평가 없으면 null)
    val averageRating: Double?
        get() = if (ratingCount == 0) null else (ratingSum * 10.0 / ratingCount).roundToLong() / 10.0

    // 프로필 업데이트
    fun updateProfile(intro: String, bankName: String, accountHolder: String, accountNumber: String) {
        this.intro = intro
        this.bankName = bankName
        this.accountHolder = accountHolder
        this.accountNumber = accountNumber
    }

    // 노쇼 횟수 증가
    fun increaseNoShowCount() {
        noShowCount++
    }

    // 저장된 등급을 새 등급으로 바꾼다. 이력은 호출하는 쪽(서비스)에서 남긴다.
    fun updateGrade(newGrade: EscortGrade) {
        grade = newGrade
    }

    // 등급 배지 기준: 완료 횟수와 노쇼 횟수로 등급을 계산한다 (저장은 하지 않는다)
    fun calculateGrade(): EscortGrade {
        val grade = when {
            completedCount >= 30 -> EscortGrade.EGGPLANT
            completedCount >= 10 -> EscortGrade.FLOWER
            completedCount >= 5 -> EscortGrade.SPROUT
            else -> EscortGrade.SEED
        }

        return when {
            noShowCount >= 3 -> lowerGrade(grade, 2)
            noShowCount >= 2 -> lowerGrade(grade, 1)
            else -> grade
        }
    }

    // 노쇼에 따른 등급 하락 (SEED 아래로는 내려가지 않는다)
    private fun lowerGrade(grade: EscortGrade, steps: Int): EscortGrade =
        EscortGrade.entries[(grade.ordinal - steps).coerceAtLeast(0)]
}