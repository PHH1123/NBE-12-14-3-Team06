package com.back.nbe12142team06.domain.education.repository

import com.back.nbe12142team06.domain.education.entity.EducationVideo
import org.springframework.data.jpa.repository.JpaRepository

interface EducationVideoRepository : JpaRepository<EducationVideo, Long> {

    fun findAllByRequiredTrue(): List<EducationVideo>

    // 필수 영상 수
    fun countByRequiredTrue(): Long
}