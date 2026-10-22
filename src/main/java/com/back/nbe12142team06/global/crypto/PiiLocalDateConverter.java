package com.back.nbe12142team06.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Converter
@RequiredArgsConstructor
public class PiiLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final PiiConverter piiConverter;

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) {
            return null;
        }
        return this.piiConverter.convertToDatabaseColumn(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return LocalDate.parse(this.piiConverter.convertToEntityAttribute(dbData));
    }
}
