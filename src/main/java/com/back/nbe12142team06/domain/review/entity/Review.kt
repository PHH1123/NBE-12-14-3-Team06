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
    var application: Application? = null
        protected set

    @field:NotNull(message = "별점은 필수입니다.")
    @field:Min(value = 1, message = "별점은 1점 이상이어야 합니다.")
    @field:Max(value = 5, message = "별점은 5점 이하여야 합니다.")
    @field:Column(nullable = false, columnDefinition = "SMALLINT")
    var rating: Int? = null
        protected set

    // Java @Builder.Default: tags 미지정 시 빈 HashSet, 명시적인 null은 그대로 유지한다.
    @field:BatchSize(size = 100)
    @field:Enumerated(EnumType.STRING)
    @field:ElementCollection(fetch = FetchType.LAZY)
    @field:CollectionTable(name = "review_tag", joinColumns = [JoinColumn(name = "review_id")])
    @field:Column(name = "tag", length = 30, nullable = false)
    var tags: Set<ReviewTag>? = HashSet()
        protected set

    @field:Column(length = 500)
    var content: String? = null
        protected set

    companion object {
        @JvmStatic
        fun builder(): ReviewBuilder = ReviewBuilder()
    }

    class ReviewBuilder {
        private var id: Long? = null
        private var application: Application? = null
        private var rating: Int? = null
        private var tags: Set<ReviewTag>? = null
        private var tagsSet: Boolean = false
        private var content: String? = null

        fun id(value: Long?) = apply { id = value }
        fun application(value: Application?) = apply { application = value }
        fun rating(value: Int?) = apply { rating = value }
        fun tags(value: Set<ReviewTag>?) = apply {
            tags = value
            tagsSet = true
        }
        fun content(value: String?) = apply { content = value }

        fun build(): Review = Review().apply {
            this.id = this@ReviewBuilder.id
            this.application = this@ReviewBuilder.application
            this.rating = this@ReviewBuilder.rating
            if (this@ReviewBuilder.tagsSet) {
                this.tags = this@ReviewBuilder.tags
            }
            this.content = this@ReviewBuilder.content
        }
    }
}
