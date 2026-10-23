package com.back.nbe12142team06.global.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor
import org.springframework.security.crypto.encrypt.BytesEncryptor
import java.util.*
import javax.crypto.spec.SecretKeySpec

@Configuration
class CryptoConfig {

    // 개인정보 양방향 암호화 (AES-256-GCM)
    @Bean
    fun piiBytesEncryptor(
        @Value("\${custom.crypto.encryption-key}") base64Key: String,
    ): BytesEncryptor {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == KEY_LENGTH) { "Invalid key length. Key needs 32 Bytes length." }

        return AesGcmBytesEncryptor.withSecretKey(SecretKeySpec(keyBytes, "AES")).build()
    }

    companion object {
        private const val KEY_LENGTH = 32 // AES-256
    }
}