package com.back.nbe12142team06.domain.report.repository

import com.back.nbe12142team06.domain.report.entity.Report
import org.springframework.data.jpa.repository.JpaRepository

interface ReportRepository : JpaRepository<Report, Long> {

    // 동행 1건당 보고서 1개이므로 Optional
    fun findByApplicationId(applicationId: Long): Report?

    // 중복 작성 방지
    fun existsByApplicationId(applicationId: Long): Boolean
}
