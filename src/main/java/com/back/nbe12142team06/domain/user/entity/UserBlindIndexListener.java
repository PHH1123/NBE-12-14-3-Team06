package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.global.crypto.BlindIndexer;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// User 를 저장(INSERT)·수정(UPDATE)하기 직전에 JPA 가 자동으로 호출한다.
// 어디서 저장하든 email·phoneNum 블라인드 인덱스가 항상 최신 값으로 채워진다.
@Component
@RequiredArgsConstructor
public class UserBlindIndexListener {

    private final BlindIndexer blindIndexer;

    @PrePersist
    @PreUpdate
    public void fillBlindIndex(User user) {
        // 탈퇴 회원은 해시를 비워 같은 이메일·전화번호로 재가입할 수 있게 한다
        // (탈퇴 시 email 이 "deleted_7" 로 바뀌는데, 그 값으로 해시를 만들면 안 된다)
        if (user.isDeleted()) {
            user.updateBlindIndex(null, null);
            return;
        }

        user.updateBlindIndex(
                this.blindIndexer.hashEmail(user.getEmail()),
                this.blindIndexer.hashPhoneNum(user.getPhoneNum())
        );
    }
}