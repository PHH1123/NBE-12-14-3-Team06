package com.back.nbe12142team06.domain.application.entity

import com.back.nbe12142team06.domain.application.enums.EscortProgress
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
import java.time.LocalDateTime

@Entity
class EscortProgressLog protected constructor() {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "application_id", nullable = false)
    lateinit var application: Application
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var progress: EscortProgress = EscortProgress.NOT_STARTED
        protected set

    @field:Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: LocalDateTime
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(length = 20)
    var source: ProgressSource? = null
        protected set

    enum class ProgressSource {
        BUTTON,
        AUTO_GPS,
    }

    // Kotlin 코드에서는 이름 있는 인자로 생성할 수 있다.
    constructor(
        application: Application,
        progress: EscortProgress = EscortProgress.NOT_STARTED,
        occurredAt: LocalDateTime,
        source: ProgressSource? = null,
    ) : this() {
        this.application = application
        this.progress = progress
        this.occurredAt = occurredAt
        this.source = source
    }

}
