package com.back.nbe12142team06.domain.education.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "education_video",
    check = [CheckConstraint(name = "chk_duration_sec", constraint = "duration_sec > 0")],
)
class EducationVideo internal constructor() {  // JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 제목
    @Column(nullable = false, length = 100)
    lateinit var title: String
        protected set

    // url
    @Column(nullable = false, length = 500)
    lateinit var url: String
        protected set

    // 영상 길이
    @Column(nullable = false)
    var durationSec: Int = 0
        protected set

    // 필수 여부
    @Column(nullable = false)
    var required: Boolean = false
        protected set

    val requiredId : Long
        get() = id ?: error("ID가 NULL입니다.")

    constructor(title: String, url: String, durationSec: Int, required: Boolean) : this() {
        this.title = title
        this.url = url
        this.durationSec = durationSec
        this.required = required
    }
}