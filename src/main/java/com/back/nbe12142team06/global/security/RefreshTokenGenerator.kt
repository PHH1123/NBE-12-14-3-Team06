package com.back.nbe12142team06.global.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

object RefreshTokenGenerator {
    private val RANDOM = SecureRandom() // 강한 난수 생성기 사용했습니다.`
    private const val TOKEN_BYTES = 32 // 256 비트짜리 난수 생성 (브루트 포싱 방어)

    // 난수 생성
    fun generate(): String {
        // 지정한 바이트 길이만큼의 난수 생성
        val bytes = ByteArray(TOKEN_BYTES).also(RANDOM::nextBytes)


        /*
        난수로 제어 문자나 비출력 문자가 나올 수 있기 때문에
        Base64로 인코딩 getUrlEncoder인 이유는 일반 인코더는 +, /를 사용하는데
        이는 URL이나 쿠키에서 공백이나 경로 구분자로 해석한다고 합니다.
        getUrlEncoder는 이걸 -, _로 바꿔준다고합니다.
         */
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes) // "="으로 패딩하는 것을 방지 (나중에 모르고 =를 기준으로 자를 수도 있어서 추가했습니다.)
    }

    // DB에는 해시값만 저장
    fun hash(rawToken: String): String {
        // SHA-256 해시 후 16진수 문자열로 반환
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray())
        return HexFormat.of().formatHex(digest)
    }
}
