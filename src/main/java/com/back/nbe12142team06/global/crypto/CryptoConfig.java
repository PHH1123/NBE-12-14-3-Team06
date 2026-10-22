package com.back.nbe12142team06.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class CryptoConfig {

    private static final int KEY_LENGTH = 32;


    @Bean
    // 개인정보 양방향 암호화 (AES-256-GCM)
    public BytesEncryptor piiBytesEncryptor(
            @Value("${custom.crypto.encryption-key}") String base64Key
    ) {
       byte[] keyBytes = Base64.getDecoder().decode(base64Key);

       if (keyBytes.length != KEY_LENGTH) {
           throw new IllegalArgumentException("Invalid key length. Key needs 32 Bytes length.");
       }

       return AesGcmBytesEncryptor.withSecretKey(new SecretKeySpec(keyBytes, "AES")).build();
    }

}
