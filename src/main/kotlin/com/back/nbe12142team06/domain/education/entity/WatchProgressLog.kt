package com.back.nbe12142team06.domain.education.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "watch_progress_log")
class WatchProgressLog internal constructor() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 교육 진행 상황
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_progress_id", nullable = false)
    lateinit var educationProgress: EducationProgress
        protected set

    // 클라이언트가 보고한 재생 위치
    @Column(nullable = false)
    var positionSec: Double = 0.0
        protected set

    // 서버가 받은 시각
    @Column(nullable = false)
    lateinit var receivedAt: LocalDateTime
        protected set

    // 정상 시청으로 인정됐는지
    @Column(nullable = false)
    var accepted: Boolean = false
        protected set

    constructor(
        educationProgress: EducationProgress,
        positionSec: Double,
        receivedAt: LocalDateTime,
        accepted: Boolean,
    ) : this() {
        this.educationProgress = educationProgress
        this.positionSec = positionSec
        this.receivedAt = receivedAt
        this.accepted = accepted
    }
}