package com.back.nbe12142team06.global.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class PiiConverterTest {
    // 키 생성
    private static String base64Key(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }

    // 암호화기 설정
    private static PiiConverter converter(int fill) {
        return new PiiConverter(new CryptoConfig().piiBytesEncryptor(base64Key(fill)));
    }

    private final PiiConverter piiConverter = converter(1);

    @Test
    @DisplayName("[PiiConverterTest] DB에 저장되는 값은 평문이 아닌 BASE64")
    void t1() {

        String plain = "김춘식 user1@user.user 010-1234-5678";

        String encrypted = piiConverter.convertToDatabaseColumn(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(piiConverter.convertToEntityAttribute(encrypted)).isEqualTo(plain);
    }
}
