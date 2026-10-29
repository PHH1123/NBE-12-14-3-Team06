package com.back.nbe12142team06.domain.education.entity

import com.back.nbe12142team06.domain.user.entity.EscortProfile
import jakarta.persistence.*
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(
    name = "education_progress",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_education_progress_escort_video",
            columnNames = ["escort_profile_id", "education_video_id"],
        ),
    ],
)
class EducationProgress internal constructor() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escort_profile_id", nullable = false)
    lateinit var escortProfile: EscortProfile
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_video_id", nullable = false)
    lateinit var educationVideo: EducationVideo
        protected set

    // 정상 시청으로 인정된 재생 위치
    @Column(nullable = false)
    var maxWatchedSec: Double = 0.0
        protected set

    // 마지막으로 재생 위치를 받은 시각 (아직 없으면 null)
    var lastReceivedAt: LocalDateTime? = null
        protected set

    // 영상 시청 완료 여부
    @Column(nullable = false)
    var completed: Boolean = false
        protected set

    // 영상 시청 완료 시각
    var completedAt: LocalDateTime? = null
        protected set

    constructor(escortProfile: EscortProfile, educationVideo: EducationVideo) : this() {
        this.escortProfile = escortProfile
        this.educationVideo = educationVideo
    }

    // 재생 위치를 반영하고 인정 여부를 반환
    fun record(positionSec: Double, now: LocalDateTime): Boolean {
        // 허용 범위는 이번 수신 시각을 기록하기 전에 계산한다
        val allowedMax = maxWatchedSec + creditedSec(now) * RATE_TOLERANCE + GRACE_SEC
        lastReceivedAt = now

        if (positionSec !in 0.0..allowedMax) return false

        maxWatchedSec = maxOf(maxWatchedSec, positionSec)

        if (!completed && maxWatchedSec >= educationVideo.durationSec - END_TOLERANCE_SEC) {
            completed = true
            completedAt = now
        }
        return true
    }

    // 직전 수신 이후 경과 시간을 0 ~ MAX_CREDIT_SEC 범위로 제한
    private fun creditedSec(now: LocalDateTime): Long {
        val last = lastReceivedAt ?: return 0
        return Duration.between(last, now).toSeconds().coerceIn(0, MAX_CREDIT_SEC)
    }

    companion object {
        // 하트비트 1회당 인정하는 최대 경과 시간
        private const val MAX_CREDIT_SEC = 15L

        // 네트워크 지연 등을 고려한 허용 배율
        private const val RATE_TOLERANCE = 1.2

        // 추가 여유 시간
        private const val GRACE_SEC = 2.0

        // 영상 끝부분 허용 오차
        private const val END_TOLERANCE_SEC = 1.0
    }
}