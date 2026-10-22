package com.back.nbe12142team06.global.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
    @DisplayName("[PiiConverter] 암호를 복호화하면 원문이 나온다")
    void t1() {

        String plain = "김춘식 user1@user.user 010-1234-5678";

        String encrypted = piiConverter.convertToDatabaseColumn(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(piiConverter.convertToEntityAttribute(encrypted)).isEqualTo(plain);
    }

    @Test
    @DisplayName("[PiiConverter] DB에 저장되는 값은 평문이 아닌 BASE64")
    void t2() {
        String encrypted = piiConverter.convertToDatabaseColumn("user1@user.user");

        assertThat(encrypted).doesNotContain("user1@user.user");

        assertThatNoException().isThrownBy(() -> Base64.getDecoder().decode(encrypted));
        assertThat(Base64.getDecoder().decode(encrypted)).isNotEmpty();
        assertThat(encrypted).matches("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");
    }


    @Test
    @DisplayName("[PiiConverter] 같은 값을 암호화해도 결과가 다름")
    void t3() {
        String first = piiConverter.convertToDatabaseColumn("user1@user.user");
        String second = piiConverter.convertToDatabaseColumn("user1@user.user");

        assertThat(first).isNotEqualTo(second);
        assertThat(piiConverter.convertToEntityAttribute(first)).isEqualTo(piiConverter.convertToEntityAttribute(second));
    }


    @Test
    @DisplayName("[PiiConverter] null은 암호화해도 null")
    void t4() {
        assertThat(piiConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(piiConverter.convertToEntityAttribute(null)).isNull();
    }
}
