package com.back.nbe12142team06.domain.report.summarizer

import com.back.nbe12142team06.domain.report.dto.ReportSummary
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Spring AI(ChatClient)로 Anthropic Claude 에게 진료 보고서를 요약시킨다.
 *
 * 여기로 넘어오는 content 는 이미 `ReportMasker` 로 실명·연락처가 가려진 텍스트다.
 * 원문을 그대로 외부로 보내지 않는다.
 */
@Component
@Profile("!test")
class ClaudeReportSummarizer(
    builder: ChatClient.Builder,
    @Value("classpath:prompts/report-summary/\${custom.ai.report-summary.prompt-version:v2}.txt")
    systemPrompt: Resource,
) : ReportSummarizer {

    private val chatClient: ChatClient = builder.defaultSystem(systemPrompt, StandardCharsets.UTF_8).build()

    // 응답에 우리가 모르는 필드가 섞여도 깨지지 않도록 설정한다.
    private val objectMapper: ObjectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun summarize(maskedContent: String): ReportSummary {
        val response = chatClient.prompt()
            .user("다음 진료 보고서를 요약해줘.\n\n$maskedContent")
            .call()
            .chatResponse()

        return parseSummary(extractText(response))
    }

    /**
     * 응답 텍스트에서 요약 JSON 을 꺼내 변환한다.
     *
     * entity() 를 쓰지 않는 이유: 모델이 JSON 을 ```json 코드 블록으로 감싸 보내면
     * entity() 의 변환기가 내용을 비워 버려 "No content to map" 오류가 난다.
     * 그래서 첫 번째 여는 중괄호부터 마지막 닫는 중괄호까지만 직접 잘라 파싱한다.
     */
    private fun parseSummary(text: String?): ReportSummary {
        if (text == null) {
            throw IllegalStateException("Claude 응답이 비어 있습니다.")
        }

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')

        if (start < 0 || end <= start) {
            // 응답 본문에는 진료 내용이 들어 있을 수 있으므로 로그에 싣지 않는다.
            throw IllegalStateException("응답에서 JSON 을 찾지 못했습니다. (응답 길이=${text.length})")
        }

        try {
            return objectMapper.readValue(text.substring(start, end + 1), ReportSummary::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Claude 응답을 요약 형식으로 변환하지 못했습니다.", e)
        }
    }

    private fun extractText(response: ChatResponse?): String? {
        if (response == null) {
            return null
        }

        return response.results.asSequence()
            .map { it.output }
            .filter { !it.metadata.containsKey("signature") && !it.metadata.containsKey("data") }
            .map { it.text }
            .firstOrNull { !it.isNullOrBlank() }
    }
}
