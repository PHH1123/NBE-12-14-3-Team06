package com.back.nbe12142team06.domain.report.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

// ClaudeReportSummarizer 와 ReportSummaryUpdater 가 쓰는 Jackson(com.fasterxml)으로
// ReportSummary 를 읽고 쓸 수 있는지 확인한다. 실제 Claude 호출 경로는 테스트(Fake 사용)에서 타지 않아 따로 둔다.
class ReportSummaryJsonTest {

    private val objectMapper: ObjectMapper = ObjectMapper()

    @Test
    @DisplayName("요약 JSON 을 ReportSummary 로 읽는다 (모르는 필드는 무시, 없는 항목은 null)")
    fun 요약_JSON_읽기() {
        val json = """
            {"visitPurpose":"정기 검진","diagnosis":"특이 소견 없음","unknown":"무시됨"}
        """.trimIndent()

        val summary = objectMapper.readValue(json, ReportSummary::class.java)

        assertThat(summary.visitPurpose).isEqualTo("정기 검진")
        assertThat(summary.diagnosis).isEqualTo("특이 소견 없음")
        assertThat(summary.prescription).isNull()
        assertThat(summary.nextVisit).isNull()
        assertThat(summary.caregiverNote).isNull()
    }

    @Test
    @DisplayName("ReportSummary 를 JSON 으로 쓰고 다시 읽으면 같은 값이다")
    fun 요약_JSON_왕복() {
        val original = ReportSummary("정기 검진", "특이 소견 없음", "처방 없음", "1개월 후 재방문", null)

        val json = objectMapper.writeValueAsString(original)
        val restored = objectMapper.readValue(json, ReportSummary::class.java)

        assertThat(json).contains("\"visitPurpose\":\"정기 검진\"")
        assertThat(restored).isEqualTo(original)
    }
}
