package com.back.nbe12142team06.domain.review.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.BatchSize

@Entity
@Table(name = "review")
class Review protected constructor() : BaseTimeEntity() {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "application_id", nullable = false, unique = true)
    lateinit var application: Application
        protected set

    @field:NotNull(message = "별점은 필수입니다.")
    @field:Min(value = 1, message = "별점은 1점 이상이어야 합니다.")
    @field:Max(value = 5, message = "별점은 5점 이하여야 합니다.")
    @field:Column(nullable = false, columnDefinition = "SMALLINT")
    var rating: Int = 0
        protected set

    // 태그를 선택하지 않으면 빈 컬렉션을 사용한다.
    @field:BatchSize(size = 100)
    @field:Enumerated(EnumType.STRING)
    @field:ElementCollection(fetch = FetchType.LAZY)
    @field:CollectionTable(name = "review_tag", joinColumns = [JoinColumn(name = "review_id")])
    @field:Column(name = "tag", length = 30, nullable = false)
    var tags: Set<ReviewTag> = HashSet()
        protected set

    @field:Column(length = 500)
    var content: String? = null
        protected set

    // 필수 값은 생성자로 받고, 선택 입력만 기본값을 제공한다.
    constructor(
        application: Application,
        rating: Int,
        tags: Set<ReviewTag> = HashSet(),
        content: String? = null,
    ) : this() {
        this.application = application
        this.rating = rating
        this.tags = tags
        this.content = content
    }
}
