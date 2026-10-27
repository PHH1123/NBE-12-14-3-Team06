package com.back.nbe12142team06.domain.report.controller

import com.back.nbe12142team06.domain.report.dto.ReportSummary
import com.back.nbe12142team06.domain.report.summarizer.ClaudeReportSummarizer
import com.back.nbe12142team06.domain.report.summarizer.ReportSummarizer
import jakarta.validation.constraints.Pattern
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 프롬프트 실험용. 저장 없이 요약 결과만 돌려준다. dev 프로필에서만 등록된다.
@Profile("dev")
@RestController
@RequestMapping("/api/dev/report-summary")
class DevReportSummaryController(
    private val chatClientBuilder: ChatClient.Builder,
) {

    @PostMapping
    fun preview(
        @RequestParam(defaultValue = "v1") @Pattern(regexp = "v\\d+") version: String,
        @RequestBody content: String,
    ): ReportSummary {

        val summarizer: ReportSummarizer = ClaudeReportSummarizer(
            chatClientBuilder.clone(),
            ClassPathResource("prompts/report-summary/$version.txt"),
        )

        return summarizer.summarize(content)
    }
}
