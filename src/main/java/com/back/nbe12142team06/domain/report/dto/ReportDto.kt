package com.back.nbe12142team06.domain.report.dto

import com.back.nbe12142team06.domain.report.entity.Report
import java.time.LocalDateTime

// 진료 보고서 조회용
@JvmRecord
data class ReportDto(
    val id: Long?,
    val applicationId: Long?,
    val clientName: String?,
    val title: String,
    val department: String,  // 화면에 그대로 쓸 수 있도록 한글 과목명으로 내려준다
    val purpose: String,
    val originContent: String,
    val notes: String?,
    val aiSummary: String?,
    val summarizedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    constructor(report: Report) : this(
        report.id,
        report.application.id,
        report.application.post.client.name,
        report.title,
        report.department.description,
        report.purpose,
        report.originContent,
        report.notes,
        report.aiSummary,
        report.summarizedAt,
        report.createdAt,
        report.updatedAt,
    )
}
