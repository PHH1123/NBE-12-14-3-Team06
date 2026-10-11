package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_status_history")
public class UserStatusHistory {

    private static final int SUSPENSION_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 대상 회원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 계정 상태를 변경한 관리자 ID
    @Column(name = "admin_id")
    private Long adminId;

    // 바뀐 계정 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // 변경 이유
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    // SUSPENDED 일때 정지가 풀리는 시각
    private LocalDateTime endAt;

    // 이력 생성 시간
    @CreatedDate
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;


    @Builder(access = AccessLevel.PRIVATE)
    private UserStatusHistory(User user, Long adminId, UserStatus status, String reason, LocalDateTime endAt) {
        this.user = user;
        this.adminId = adminId;
        this.status = status;
        this.reason = reason;
        this.endAt = endAt;
    }

    // 회원가입
    public static UserStatusHistory signup(User user){
        return builder()
                .user(user)
                .status(UserStatus.ACTIVE)
                .reason("회원가입")
                .build();
    }

    // 기간 정지
    static UserStatusHistory suspend(User user, Long adminId, String reason) {
        return builder()
                .user(user)
                .adminId(adminId)
                .status(UserStatus.SUSPENDED)
                .reason(reason)
                .endAt(LocalDateTime.now().plusDays(SUSPENSION_DAYS))
                .build();
    }

    // 영구 정지
    static UserStatusHistory ban(User user, Long adminId, String reason) {
        return builder()
                .user(user)
                .adminId(adminId)
                .status(UserStatus.BANNED)
                .reason(reason)
                .build();
    }

    // 관리자 해제
    static UserStatusHistory release(User user, Long adminId, String reason) {
        return builder()
                .user(user)
                .adminId(adminId)
                .status(UserStatus.ACTIVE)
                .reason(reason)
                .build();
    }

    // 기간 정지 기간 만료 시 해제
    static UserStatusHistory expire(User user){
        return builder()
                .user(user)
                .status(UserStatus.ACTIVE)
                .reason("기간 정지 만료")
                .build();
    }
}
