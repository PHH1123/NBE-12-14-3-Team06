package com.back.nbe12142team06.global.crypto

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class PiiConverterTest {

    private val piiConverter = converter(1)

    @Test
    @DisplayName("[PiiConverter] 암호를 복호화하면 원문이 나온다")
    fun t1() {
        val plain = "김춘식 user1@user.user 010-1234-5678"

        val encrypted = piiConverter.convertToDatabaseColumn(plain)

        assertThat(encrypted).isNotEqualTo(plain)
        assertThat(piiConverter.convertToEntityAttribute(encrypted)).isEqualTo(plain)
    }

    @Test
    @DisplayName("[PiiConverter] DB에 저장되는 값은 평문이 아닌 BASE64")
    fun t2() {
        val encrypted = piiConverter.convertToDatabaseColumn("user1@user.user")

        assertThat(encrypted).doesNotContain("user1@user.user")

        assertThatNoException().isThrownBy { Base64.getDecoder().decode(encrypted) }
        assertThat(Base64.getDecoder().decode(encrypted)).isNotEmpty()
        assertThat(encrypted).matches("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$")
    }

    @Test
    @DisplayName("[PiiConverter] 같은 값을 암호화해도 결과가 다름")
    fun t3() {
        val first = piiConverter.convertToDatabaseColumn("user1@user.user")
        val second = piiConverter.convertToDatabaseColumn("user1@user.user")

        assertThat(first).isNotEqualTo(second)
        assertThat(piiConverter.convertToEntityAttribute(first))
            .isEqualTo(piiConverter.convertToEntityAttribute(second))
    }

    @Test
    @DisplayName("[PiiConverter] null은 암호화해도 null")
    fun t4() {
        assertThat(piiConverter.convertToDatabaseColumn(null)).isNull()
        assertThat(piiConverter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    @DisplayName("[PiiConverter] 다른 키로는 복호화 불가")
    fun t5() {
        val encrypted = piiConverter.convertToDatabaseColumn("user1@user.user")

        assertThatThrownBy { converter(2).convertToEntityAttribute(encrypted) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    @DisplayName("[PiiConverter] 암호화 키가 32바이트가 아니면 예외 발생")
    fun t6() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

        assertThatThrownBy { CryptoConfig().piiBytesEncryptor(shortKey) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("[PiiConverter] 생년월일(LocalDate)도 암/복호화 가능")
    fun t7() {
        val dateConverter = PiiLocalDateConverter(piiConverter)
        val birthDate = LocalDate.of(1990, 5, 20)

        val encrypted = dateConverter.convertToDatabaseColumn(birthDate)

        assertThat(encrypted).doesNotContain("1990")
        assertThat(dateConverter.convertToEntityAttribute(encrypted)).isEqualTo(birthDate)
        assertThat(dateConverter.convertToDatabaseColumn(null)).isNull()
        assertThat(dateConverter.convertToEntityAttribute(null)).isNull()
    }

    companion object {
        // 32바이트를 같은 값으로 채운 테스트용 키
        private fun base64Key(fill: Int): String =
            Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })

        // 암호화기 설정
        private fun converter(fill: Int) =
            PiiConverter(CryptoConfig().piiBytesEncryptor(base64Key(fill)))
    }
}