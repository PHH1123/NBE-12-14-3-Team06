package com.back.nbe12142team06.domain.user.entity

import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.crypto.BlindIndexer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class UserBlindIndexListenerTest {

    private val blindIndexer = createIndexer()
    private val listener = UserBlindIndexListener(blindIndexer)

    private fun createUser() = User(
        username = "user1",
        password = "testPassword",
        email = "user1@user.user",
        name = "김춘식",
        role = Role.CLIENT,
        gender = Gender.MALE,
        birthDate = LocalDate.of(1990, 5, 20),
        phoneNum = "010-1234-5678",
        region = "서울시",
    )

    @Test
    @DisplayName("[UserBlindIndexListener] 저장 전 이메일, 전화번호 해시 생성")
    fun t1() {
        val user = createUser()

        listener.fillBlindIndex(user)

        assertThat(user.emailHash).isEqualTo(blindIndexer.hashEmail("user1@user.user"))
        assertThat(user.phoneNumHash).isEqualTo(blindIndexer.hashPhoneNum("01012345678"))
    }

    @Test
    @DisplayName("[UserBlindIndexListener] 이메일, 전화번호를 수정 시 해시값도 변경")
    fun t2() {
        val user = createUser()
        listener.fillBlindIndex(user)
        val oldEmailHash = user.emailHash

        user.updateUser(
            password = "testPassword",
            email = "new@user.user",
            name = "김춘식",
            birthDate = LocalDate.of(1990, 5, 20),
            phoneNum = "010-9999-9999",
            region = "서울시",
        )
        listener.fillBlindIndex(user)

        assertThat(user.emailHash)
            .isNotEqualTo(oldEmailHash)
            .isEqualTo(blindIndexer.hashEmail("new@user.user"))
        assertThat(user.phoneNumHash).isEqualTo(blindIndexer.hashPhoneNum("010-9999-9999"))
    }

    @Test
    @DisplayName("[UserBlindIndexListener] 탈퇴 회원은 해시값이 null")
    fun t3() {
        val user = createUser()
        user.deleteUser()
        user.updateBlindIndex("stale", "stale")

        listener.fillBlindIndex(user)

        assertThat(user.emailHash).isNull()
        assertThat(user.phoneNumHash).isNull()
    }

    companion object {
        // 32바이트를 1로 채운 테스트용 키
        private fun createIndexer() =
            BlindIndexer(Base64.getEncoder().encodeToString(ByteArray(32) { 1 }))
    }
}