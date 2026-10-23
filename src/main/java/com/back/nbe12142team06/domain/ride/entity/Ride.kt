package com.back.nbe12142team06.domain.ride.entity

import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import com.back.nbe12142team06.global.exception.InvalidException
import jakarta.persistence.*

/**
 * 이동 수단 엔티티
 * 의뢰인 이동 수단 선택
 */
@Entity
class Ride(
    id: Long? = null,
    direction: RideDirection = RideDirection.TO_HOSPITAL,
    status: RideStatus = RideStatus.ACCEPTED,
    selected: RideSelect,
    post: Post,
    ) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = id
        protected set

    // 이동 방향
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var direction: RideDirection = direction
        protected set

    // 이동 수단 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var rideStatus = status
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var selected: RideSelect = selected
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "post_id")
    var post: Post = post
        protected set

    val requiredId: Long
        get() = id ?: error("ID가 NULL입니다.")

    // 이동수단 선택 시 정보 업데이트
    fun updateSelect(selected: RideSelect) {
        this.selected = selected
    }

    // 이동 상태 변경
    fun updateStatus(rideStatus: RideStatus) {
        if (!this.rideStatus.canTransitionTo(rideStatus)) {
            throw InvalidException(
                82, "'${this.rideStatus.description}' 상태의 이동은 '${rideStatus.description}' 상태로 바꿀 수 없습니다."
            )
        }
        this.rideStatus = rideStatus
    }

    // 의뢰인 검증
    fun validClient(userId: Long): Boolean {
        return post.client.id == userId
    }

    fun nextStatus(progress: EscortProgress) {
        when (this.direction to progress) {
            RideDirection.TO_HOSPITAL to EscortProgress.TO_HOSPITAL -> this.updateStatus(RideStatus.IN_PROGRESS)
            RideDirection.TO_HOSPITAL to EscortProgress.AT_HOSPITAL -> this.updateStatus(RideStatus.COMPLETED)
            RideDirection.TO_HOME to EscortProgress.TO_HOME -> this.updateStatus(RideStatus.IN_PROGRESS)
            RideDirection.TO_HOME to EscortProgress.ARRIVED_HOME -> this.updateStatus(RideStatus.COMPLETED)
            else -> {}
        }
    }
}
