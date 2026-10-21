package com.back.nbe12142team06.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class BlindIndexer {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 32;

    private final SecretKeySpec key;

    public BlindIndexer(@Value("${custom.crypto.hmac-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("블라인드 인덱스 키는 32바이트여야 합니다.");
        }

        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // 이메일: 앞뒤 공백 제거 + 소문자로 맞춘 뒤 해시
    public String hashEmail(String email) {
        if (email == null) {
            return null;
        }
        return hmac(email.strip().toLowerCase(Locale.ROOT));
    }

    // 전화번호: 숫자만 남긴 뒤 해시 (010-1234-5678 과 01012345678 을 같은 번호로 본다)
    public String hashPhoneNum(String phoneNum) {
        if (phoneNum == null) {
            return null;
        }
        return hmac(phoneNum.replaceAll("\\D", ""));
    }

    private String hmac(String value) {
        try {
            // Mac 은 여러 스레드가 같이 쓰면 안 돼서 호출마다 새로 만든다
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(this.key);
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("블라인드 인덱스 생성에 실패했습니다.", e);
        }
    }
}
