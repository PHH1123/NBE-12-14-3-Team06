package com.back.nbe12142team06.domain.report.summarizer;

import com.back.nbe12142team06.domain.report.dto.ReportSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Spring AI(ChatClient)로 Anthropic Claude 에게 진료 보고서를 요약시킨다.
 * <p>
 * 여기로 넘어오는 content 는 이미 {@code ReportMasker} 로 실명·연락처가 가려진 텍스트다.
 * 원문을 그대로 외부로 보내지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
public class ClaudeReportSummarizer implements ReportSummarizer {

    private final ChatClient chatClient;

    // 응답에 우리가 모르는 필드가 섞여도 깨지지 않도록 설정한다.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ClaudeReportSummarizer(
            ChatClient.Builder builder,
            @Value("classpath:prompts/report-summary/${custom.ai.report-summary.prompt-version:v1}.txt")
            Resource systemPrompt) {
        this.chatClient = builder.defaultSystem(systemPrompt, StandardCharsets.UTF_8).build();
    }



    @Override
    public ReportSummary summarize(String maskedContent) {
        String text = chatClient.prompt()
                .user("다음 진료 보고서를 요약해줘.\n\n" + maskedContent)
                .call()
                .content();

        return parseSummary(text);
    }

    /**
     * 응답 텍스트에서 요약 JSON 을 꺼내 변환한다.
     * <p>
     * entity() 를 쓰지 않는 이유: 모델이 JSON 을 ```json 코드 블록으로 감싸 보내면
     * entity() 의 변환기가 내용을 비워 버려 "No content to map" 오류가 난다.
     * 그래서 첫 번째 여는 중괄호부터 마지막 닫는 중괄호까지만 직접 잘라 파싱한다.
     */
    private ReportSummary parseSummary(String text) {
        if (text == null) {
            throw new IllegalStateException("Claude 응답이 비어 있습니다.");
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start < 0 || end <= start) {
            // 응답 본문에는 진료 내용이 들어 있을 수 있으므로 로그에 싣지 않는다.
            throw new IllegalStateException("응답에서 JSON 을 찾지 못했습니다. (응답 길이=%d)".formatted(text.length()));
        }

        try {
            return objectMapper.readValue(text.substring(start, end + 1), ReportSummary.class);
        } catch (Exception e) {
            throw new IllegalStateException("Claude 응답을 요약 형식으로 변환하지 못했습니다.", e);
        }
    }
}
