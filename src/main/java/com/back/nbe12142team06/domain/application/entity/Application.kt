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

    // 공고당 승인된 지원은 하나만 유지한다. 승인 취소/노쇼 시 null이 된다.
    @field:Column(name = "accepted_post_id", unique = true)
    var acceptedPostId: Long? = null
        protected set

    // PENDING/ACCEPTED일 때만 공고 ID를 가진다. 종료된 지원은 null이다.
    @field:Column(name = "active_post_id")
    var activePostId: Long? = null
        protected set

    // Kotlin 호출부에서는 필수 연관관계를 명시한 생성자를 사용할 수 있다.
    constructor(
        post: Post,
        escort: User,
        status: ApplicationStatus = ApplicationStatus.PENDING,
    ) : this() {
        this.post = post
        this.escort = escort
        this.status = status
    }

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

    companion object {
        // 남아 있는 Java 서비스·테스트와의 호환성용. Kotlin 전환 완료 뒤 제거 여부를 검토한다.
        @JvmStatic
        fun builder(): ApplicationBuilder = ApplicationBuilder()
    }

    class ApplicationBuilder {
        private var id: Long? = null
        private var post: Post? = null
        private var escort: User? = null
        private var status: ApplicationStatus = ApplicationStatus.PENDING
        private var lat: BigDecimal? = null
        private var lng: BigDecimal? = null
        private var accuracy: BigDecimal? = null
        private var locationUpdatedAt: Instant? = null
        private var acceptedPostId: Long? = null
        private var activePostId: Long? = null

        fun id(value: Long?) = apply { id = value }
        fun post(value: Post?) = apply { post = value }
        fun escort(value: User?) = apply { escort = value }
        fun status(value: ApplicationStatus) = apply { status = value }
        fun lat(value: BigDecimal?) = apply { lat = value }
        fun lng(value: BigDecimal?) = apply { lng = value }
        fun accuracy(value: BigDecimal?) = apply { accuracy = value }
        fun locationUpdatedAt(value: Instant?) = apply { locationUpdatedAt = value }
        fun acceptedPostId(value: Long?) = apply { acceptedPostId = value }
        fun activePostId(value: Long?) = apply { activePostId = value }

        fun build(): Application = Application().apply {
            this.id = this@ApplicationBuilder.id
            this@ApplicationBuilder.post?.let { this.post = it }
            this@ApplicationBuilder.escort?.let { this.escort = it }
            this.status = this@ApplicationBuilder.status
            this.lat = this@ApplicationBuilder.lat
            this.lng = this@ApplicationBuilder.lng
            this.accuracy = this@ApplicationBuilder.accuracy
            this.locationUpdatedAt = this@ApplicationBuilder.locationUpdatedAt
            this.acceptedPostId = this@ApplicationBuilder.acceptedPostId
            this.activePostId = this@ApplicationBuilder.activePostId
        }
    }
}
