package com.back.nbe12142team06.global.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class BlindIndexer(
    @Value("\${custom.crypto.hmac-key}") base64Key: String,
) {
    private val key: SecretKeySpec = run {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == KEY_LENGTH) { "블라인드 인덱스 키는 32바이트여야 합니다." }
        SecretKeySpec(keyBytes, ALGORITHM)
    }

    // 이메일: 앞뒤 공백 제거 + 소문자로 맞춘 뒤 해시
    // Java strip() 과 같은 기준으로 공백을 지운다 (Kotlin trim() 은 NBSP 까지 지워서 기존 해시와 달라짐)
    fun hashEmail(email: String?): String? =
        email?.let { hmac(it.trim { c -> Character.isWhitespace(c) }.lowercase()) }

    // 전화번호: 숫자만 남긴 뒤 해시 (010-1234-5678 과 01012345678 을 같은 번호로 본다)
    fun hashPhoneNum(phoneNum: String?): String? =
        phoneNum?.let { hmac(it.replace(NON_DIGIT, "")) }

    private fun hmac(value: String): String =
        try {
            // Mac 은 여러 스레드가 같이 쓰면 안 돼서 호출마다 새로 만든다
            val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
            HexFormat.of().formatHex(mac.doFinal(value.toByteArray()))
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("블라인드 인덱스 생성에 실패했습니다.", e)
        }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val KEY_LENGTH = 32
        private val NON_DIGIT = Regex("\\D")
    }
}