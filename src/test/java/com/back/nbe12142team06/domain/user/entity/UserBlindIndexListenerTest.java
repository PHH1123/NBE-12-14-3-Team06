package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.global.crypto.BlindIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class UserBlindIndexListenerTest {

    private final BlindIndexer blindIndexer = createIndexer();
    private final UserBlindIndexListener listener = new UserBlindIndexListener(blindIndexer);

    private static BlindIndexer createIndexer() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 1);
        return new BlindIndexer(Base64.getEncoder().encodeToString(key));
    }

    private User createUser() {
        return User.builder()
                .username("user1")
                .password("testPassword")
                .email("user1@user.user")
                .name("김춘식")
                .role(Role.CLIENT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(1990, 5, 20))
                .phoneNum("010-1234-5678")
                .region("서울시")
                .build();
    }


    @Test
    @DisplayName("[UserBlindIndexListener] 저장 전 이메일, 전화번호 해시 생성")
    void t1() {
        User user = createUser();

        listener.fillBlindIndex(user);

        assertThat(user.getEmailHash()).isEqualTo(blindIndexer.hashEmail("user1@user.user"));
        assertThat(user.getPhoneNumHash()).isEqualTo(blindIndexer.hashPhoneNum("01012345678"));
    }

    @Test
    @DisplayName("[UserBlindIndexListener] 이메일, 전화번호를 수정 시 해시값도 변경")
    void t2() {
        User user = createUser();
        listener.fillBlindIndex(user);
        String oldEmailHash = user.getEmailHash();

        user.updateUser("testPassword", "new@user.user", "김춘식",
                LocalDate.of(1990, 5, 20), "010-9999-9999", "서울시");
        listener.fillBlindIndex(user);

        assertThat(user.getEmailHash())
                .isNotEqualTo(oldEmailHash)
                .isEqualTo(blindIndexer.hashEmail("new@user.user"));
        assertThat(user.getPhoneNumHash()).isEqualTo(blindIndexer.hashPhoneNum("010-9999-9999"));
    }

}
