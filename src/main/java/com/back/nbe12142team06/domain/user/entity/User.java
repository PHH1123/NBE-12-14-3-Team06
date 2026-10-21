package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.enums.UserStatus;
import com.back.nbe12142team06.global.crypto.PiiConverter;
import com.back.nbe12142team06.global.crypto.PiiLocalDateConverter;
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity;
import com.back.nbe12142team06.global.exception.InvalidException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseSoftDeleteTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디
    @Column(nullable = false, unique = true)
    private String username;

    // 로그인 비밀번호
    @Column(nullable = false)
    private String password;

    // 회원 이메일
    @Column(nullable = false, unique = true)
    private String email;

    // 회원 실명
    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, length = 255)
    private String name;

    // 회원 역할
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 회원 성별
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    // 회원 생년월일
    @Convert(converter = PiiLocalDateConverter.class)
    @Column(nullable = false, length = 255)
    private LocalDate birthDate;

    // 회원 전화번호
    @Column(nullable = false, length = 20, unique = true)
    private String phoneNum;

    // 지역
    @Column(nullable = false, length = 50)
    private String region;

    // 계정 활성, 일시 정지, 영구 정지 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus userStatus = UserStatus.ACTIVE;


    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private Long version;

    @Builder
    public User(String username, String password, String email, String name, Role role, Gender gender, LocalDate birthDate, String phoneNum, String region) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.name = name;
        this.role = role;
        this.gender = gender;
        this.birthDate = birthDate;
        this.phoneNum = phoneNum;
        this.region = region;
    }

    // 회원 정보 수정
    public void updateUser(String password, String email, String name, LocalDate birthDate, String phoneNum, String region){
        this.password = password;
        this.email = email;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNum = phoneNum;
        this.region = region;
    }

    // 회원 탈퇴 시
    public void deleteUser(){
        this.setDeletedAt(LocalDateTime.now());
        this.username = "deleted_%d".formatted(this.id);
        this.email = "deleted_%d".formatted(this.id);
        this.phoneNum = "deleted_%d".formatted(this.id);
    }

    // 탈퇴 여부
    public boolean isDeleted() {
        return this.getDeletedAt() != null;
    }

    // ACTIVE 계정 여부
    public boolean isActive() {
        return this.userStatus == UserStatus.ACTIVE;
    }

    // 상태 변경
    private void changeStatus(UserStatus newStatus) {
        // 현재 상태
        UserStatus prevStatus = this.userStatus;

        // 같은 상태로의 변경은 불가
        if (prevStatus == newStatus) {
            throw new InvalidException(
                    8,
                    "이미 %s 상태인 계정입니다.".formatted(prevStatus.getDescription())
            );
        }
        // BANNED -> SUSPENDED 변경 불가
        else if (!prevStatus.canTransitionTo(newStatus)) {
            throw new InvalidException(
                    9,
                    "영구 정지된 계정은 해제만 가능합니다."
            );
        }

        // 유저 테이블의 상태 변경
        this.userStatus = newStatus;
    }

    // 기간 정지
    public UserStatusHistory suspend(Long adminId, String reason) {
        changeStatus(UserStatus.SUSPENDED);
        return UserStatusHistory.suspend(this, adminId, reason);
    }

    // 영구 정지
    public UserStatusHistory ban(Long adminId, String reason) {
        changeStatus(UserStatus.BANNED);
        return UserStatusHistory.ban(this, adminId, reason);
    }

    // 관리자의 정지 해제
    public UserStatusHistory release(Long adminId, String reason) {
        changeStatus(UserStatus.ACTIVE);
        return UserStatusHistory.release(this, adminId, reason);
    }

    // 정지 기간 만료 시 해제
    public UserStatusHistory expire() {

        if (this.userStatus == UserStatus.BANNED) {
            throw new InvalidException(
                    9,
                    "영구 정지된 계정은 해제만 가능합니다."
            );
        }
        changeStatus(UserStatus.ACTIVE);
        return UserStatusHistory.expire(this);
    }
}
