package com.back.nbe12142team06.global.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class BaseSoftDeleteTimeEntity : BaseTimeEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set
}
