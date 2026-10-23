package com.back.nbe12142team06.global.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Converter
class PiiLocalDateConverter(
    private val piiConverter: PiiConverter,
) : AttributeConverter<LocalDate?, String?> {

    // LocalDate -> "yyyy-MM-dd" -> 암호화 (null 은 그대로 null)
    override fun convertToDatabaseColumn(attribute: LocalDate?): String? =
        attribute?.let { piiConverter.convertToDatabaseColumn(it.toString()) }

    // 복호화 -> "yyyy-MM-dd" -> LocalDate (null 은 그대로 null)
    override fun convertToEntityAttribute(dbData: String?): LocalDate? =
        piiConverter.convertToEntityAttribute(dbData)?.let(LocalDate::parse)
}