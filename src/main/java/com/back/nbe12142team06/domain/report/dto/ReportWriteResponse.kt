package com.back.nbe12142team06.domain.report.dto

import com.back.nbe12142team06.domain.report.entity.Report
import java.time.LocalDateTime

data class ReportWriteResponse(
    val id: Long?,
    val applicationId: Long?,
    val title: String,
    val department: String,
    val purpose: String,
    val originContent: String,
    val notes: String?,
    val aiSummary: String?,
    val summarizedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
) {
    constructor(report: Report) : this(
        report.id,
        report.application.id,
        report.title,
        report.department.description,
        report.purpose,
        report.originContent,
        report.notes,
        report.aiSummary,      // 요약 실패 시 null 로 남는다
        report.summarizedAt,   // 동일
        report.createdAt,
    )
}
