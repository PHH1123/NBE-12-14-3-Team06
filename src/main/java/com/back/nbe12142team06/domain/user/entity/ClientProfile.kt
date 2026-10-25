package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.global.crypto.PiiConverter
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "client_profiles")
class ClientProfile internal constructor() : BaseTimeEntity() {  // JPA용 기본 생성자

    // 회원 개인 키 ID (@MapsId 로 user 의 id 를 그대로 사용)
    @Id
    var userId: Long? = null
        protected set

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    lateinit var user: User
        protected set

    // 보호자 실명
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var emergencyContactName: String
        protected set

    // 보호자 번호
    @Convert(converter = PiiConverter::class)
    @Column(nullable = false, length = 255)
    lateinit var emergencyContactPhone: String
        protected set

    // 의뢰인 특이사항
    @Convert(converter = PiiConverter::class)
    @Column(columnDefinition = "TEXT")
    var careNote: String? = null
        protected set

    constructor(
        user: User,
        emergencyContactName: String,
        emergencyContactPhone: String,
        careNote: String?,
    ) : this() {
        this.user = user
        this.emergencyContactName = emergencyContactName
        this.emergencyContactPhone = emergencyContactPhone
        this.careNote = careNote
    }

    fun updateProfile(emergencyContactName: String, emergencyContactPhone: String, careNote: String?) {
        this.emergencyContactName = emergencyContactName
        this.emergencyContactPhone = emergencyContactPhone
        this.careNote = careNote
    }
}