package com.back.nbe12142team06.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Converter
@RequiredArgsConstructor
public class PiiConverter implements AttributeConverter<String, String> {

    private final BytesEncryptor piiBytesEncryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        byte[] encrypted = this.piiBytesEncryptor.encrypt(attribute.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        byte[] decrypted = this.piiBytesEncryptor.decrypt(Base64.getDecoder().decode(dbData));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
