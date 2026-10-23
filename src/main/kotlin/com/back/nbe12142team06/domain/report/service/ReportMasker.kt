package com.back.nbe12142team06.domain.report.service

import com.back.nbe12142team06.domain.user.entity.User
import org.springframework.stereotype.Component

// 외부 API 전송 전 식별 정보를 가린다.
// 병명, 처방 등 진료 내용은 요약에 필요하므로 마스킹 대상이 아니다.
@Component
class ReportMasker {

    fun mask(originContent: String, client: User, escort: User): String {

        var masked = originContent

        masked = replaceName(masked, client.name, "환자분")
        masked = replaceName(masked, escort.name, "동행인")

        return PHONE.replace(masked, "[연락처]")
    }

    private fun replaceName(content: String, name: String?, replacement: String): String {
        if (name.isNullOrBlank()) {
            return content
        }
        return content.replace(name, replacement)
    }

    companion object {
        // 010-1234-5678, 01012345678 등
        private val PHONE = Regex("01[0-9][-. ]?\\d{3,4}[-. ]?\\d{4}")
    }
}
