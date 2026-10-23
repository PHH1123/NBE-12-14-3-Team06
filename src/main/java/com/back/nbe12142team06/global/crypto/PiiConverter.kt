package com.back.nbe12142team06.global.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.stereotype.Component
import java.util.*

@Component
@Converter
class PiiConverter(
    private val piiBytesEncryptor: BytesEncryptor,
) : AttributeConverter<String?, String?> {

    // 평문 -> 암호화 -> Base64 (null 은 그대로 null)
    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let {
            Base64.getEncoder().encodeToString(piiBytesEncryptor.encrypt(it.toByteArray()))
        }

    // Base64 -> 복호화 -> 평문 (null 은 그대로 null)
    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let {
            String(piiBytesEncryptor.decrypt(Base64.getDecoder().decode(it)))
        }
}