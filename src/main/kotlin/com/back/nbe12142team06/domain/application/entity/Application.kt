package com.back.nbe12142team06.domain.application.entity

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import com.back.nbe12142team06.global.exception.InvalidException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
// 취소 후 재지원이 가능해야 하므로, 종료된 지원이 아닌 진행 중인 지원에만 유니크 제약을 적용한다.
@Table(
    name = "application",
    uniqueConstraints = [UniqueConstraint(columnNames = ["active_post_id", "escort_id"])],
)
class Application protected constructor() : BaseTimeEntity() {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "post_id", nullable = false)
    lateinit var post: Post
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "escort_id", nullable = false)
    lateinit var escort: User
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var status: ApplicationStatus = ApplicationStatus.PENDING
        protected set

    // 동행 중 매니저의 마지막 위치. 첫 위치 수신 전에는 null이다.
    @field:Column(precision = 10, scale = 7)
    var lat: BigDecimal? = null
        protected set

    @field:Column(precision = 10, scale = 7)
    var lng: BigDecimal? = null
        protected set

    @field:Column
    var accuracy: BigDecimal? = null
        protected set

    @field:Column(name = "location_updated_at")
    var locationUpdatedAt: Instant? = null
        protected set

    // 동행 중 의뢰인의 마지막 위치. 매니저 위치와 똑같이 최신 1건만 덮어쓰며, 첫 위치 수신 전에는 null이다.
    @field:Column(name = "client_lat", precision = 10, scale = 7)
    var clientLat: BigDecimal? = null
        protected set

    @field:Column(name = "client_lng", precision = 10, scale = 7)
    var clientLng: BigDecimal? = null
        protected set

    @field:Column(name = "client_accuracy")
    var clientAccuracy: BigDecimal? = null
        protected set

    @field:Column(name = "client_location_updated_at")
    var clientLocationUpdatedAt: Instant? = null
        protected set

    // 공고당 승인된 지원은 하나만 유지한다. 승인 취소/노쇼 시 null이 된다.
    @field:Column(name = "accepted_post_id", unique = true)
    var acceptedPostId: Long? = null
        protected set

    // PENDING/ACCEPTED일 때만 공고 ID를 가진다. 종료된 지원은 null이다.
    @field:Column(name = "active_post_id")
    var activePostId: Long? = null
        protected set

    // 필수 연관관계는 생성 시 지정하고, DB가 생성하는 ID와 선택 상태 필드는 필요할 때만 전달한다.
    constructor(
        post: Post,
        escort: User,
        status: ApplicationStatus = ApplicationStatus.PENDING,
        id: Long? = null,
        acceptedPostId: Long? = null,
        activePostId: Long? = null,
    ) : this() {
        this.post = post
        this.escort = escort
        this.status = status
        this.id = id
        this.acceptedPostId = acceptedPostId
        this.activePostId = activePostId
    }

    val requiredId : Long
        get() = id ?: error("ID가 NULL입니다.")

    private fun syncActivePostId() {
        activePostId = if (status == ApplicationStatus.PENDING || status == ApplicationStatus.ACCEPTED) {
            post.id
        } else {
            null
        }
    }

    @PrePersist
    private fun onCreate() {
        syncActivePostId()
    }

    private fun changeStatus(next: ApplicationStatus) {
        if (!status.canTransitionTo(next)) {
            throw InvalidException(37, "변경할 수 없는 지원 상태입니다.")
        }
        status = next
        syncActivePostId()
    }

    fun accept() {
        changeStatus(ApplicationStatus.ACCEPTED)
        acceptedPostId = post.id
    }

    fun reject() {
        changeStatus(ApplicationStatus.REJECTED)
    }

    fun cancel() {
        if (status != ApplicationStatus.PENDING) {
            throw InvalidException(37, "대기 중인 지원만 일반 취소할 수 있습니다.")
        }
        changeStatus(ApplicationStatus.CANCELED)
    }

    fun noShow() {
        changeStatus(ApplicationStatus.NO_SHOW)
        acceptedPostId = null
    }

    fun cancelWithoutPenalty() {
        changeStatus(ApplicationStatus.CANCELED)
        acceptedPostId = null
    }

}
