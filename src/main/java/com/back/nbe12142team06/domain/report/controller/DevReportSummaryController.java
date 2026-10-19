package com.back.nbe12142team06.domain.report.controller;

import com.back.nbe12142team06.domain.report.dto.ReportSummary;
import com.back.nbe12142team06.domain.report.summarizer.ClaudeReportSummarizer;
import com.back.nbe12142team06.domain.report.summarizer.ReportSummarizer;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

// 프롬프트 실험용. 저장 없이 요약 결과만 돌려준다. dev 프로필에서만 등록된다.
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/report-summary")
public class DevReportSummaryController {

    private final ChatClient.Builder chatClientBuilder;

    @PostMapping
    public ReportSummary preview(
            @RequestParam(defaultValue = "v1") @Pattern(regexp = "v\\d+") String version,
            @RequestBody String content) {

        ReportSummarizer summarizer = new ClaudeReportSummarizer(
                chatClientBuilder.clone(),
                new ClassPathResource("prompts/report-summary/" + version + ".txt"));

        return summarizer.summarize(content);
    }
}
