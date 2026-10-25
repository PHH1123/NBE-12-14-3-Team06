package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.domain.user.enums.EscortGrade
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "escort_grade_history")
class EscortGradeHistory internal constructor() : BaseTimeEntity() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 등급이 바뀐 동행 매니저
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escort_id", nullable = false)
    lateinit var escortProfile: EscortProfile
        protected set

    // 변경 전 등급 (최초 등급 부여 시에는 null)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var previousGrade: EscortGrade? = null
        protected set

    // 변경 후 등급
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    lateinit var newGrade: EscortGrade
        protected set

    constructor(escortProfile: EscortProfile, previousGrade: EscortGrade?, newGrade: EscortGrade) : this() {
        this.escortProfile = escortProfile
        this.previousGrade = previousGrade
        this.newGrade = newGrade
    }
}