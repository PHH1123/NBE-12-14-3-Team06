package com.back.nbe12142team06.domain.user.entity;

import com.back.nbe12142team06.domain.user.enums.EscortGrade;
import com.back.nbe12142team06.global.crypto.PiiConverter;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "escort_profiles")
public class EscortProfile extends BaseTimeEntity {

    // 회원 개인 키 ID
    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 신원 인증 여부
    @Column(nullable = false)
    private Boolean verified = false;

    // 신원 인증 날짜, 시각
    private LocalDateTime verifiedAt;

    // 자기소개
    @Column(nullable = false, length = 500)
    private String intro;

    // 동행 완료 건수
    @Column(nullable = false)
    private Integer completedCount = 0;

    // 평점 합계
    @Column(nullable = false)
    private Integer ratingSum = 0;

    // 받은 평점 개수
    @Column(nullable = false)
    private Integer ratingCount = 0;

    // 노쇼 횟수
    @Column(nullable = false)
    private Integer noShowCount = 0;

    // 등급 (동행 완료 횟수와 노쇼 횟수로 계산해 저장한다)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SEED'")
    @Column(nullable = false, length = 20)
    private EscortGrade grade = EscortGrade.SEED;

    // 은행 이름
    @Column(nullable = false, length = 20)
    private String bankName;

    // 예금주명
    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, length = 255)
    private String accountHolder;

    // 계좌번호
    @Convert(converter = PiiConverter.class)
    @Column(nullable = false, length = 255)
    private String accountNumber;

    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private Long version;


    public EscortProfile(User user, String intro, String bankName, String accountHolder, String accountNumber){
        this.user = user;
        this.intro = intro;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
    }

    // 프로필 업데이트
    public void updateProfile(String intro, String bankName, String accountHolder, String accountNumber){
        this.intro = intro;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumber = accountNumber;
    }

    // 평균 평점 (소수점 첫째 자리, 평가 없으면 null)
    public Double getAverageRating() {
        if (this.ratingCount == 0) {
            return null;
        }
        return Math.round(this.ratingSum * 10.0 / this.ratingCount) / 10.0;
    }

    // 노쇼 횟수 증가
    public void increaseNoShowCount() {
        this.noShowCount++;
    }


    // 저장된 등급을 새 등급으로 바꾼다. 이력은 호출하는 쪽(서비스)에서 남긴다.
    public void updateGrade(EscortGrade newGrade) {
        this.grade = newGrade;
    }

    // 등급 배지 기준: 완료 횟수와 노쇼 횟수로 등급을 계산한다 (저장은 하지 않는다)
    public EscortGrade calculateGrade() {
        EscortGrade grade;

        if (completedCount >= 30) {
            grade = EscortGrade.EGGPLANT;
        } else if (completedCount >= 10) {
            grade = EscortGrade.FLOWER;
        } else if (completedCount >= 5) {
            grade = EscortGrade.SPROUT;
        } else {
            grade = EscortGrade.SEED;
        }

        if (noShowCount >= 3) {
            return lowerGrade(grade, 2);
        }
        if (noShowCount >= 2) {
            return lowerGrade(grade, 1);
        }

        return grade;
    }

    // 노쇼에 따른 등급 하락
    private EscortGrade lowerGrade(EscortGrade grade, int steps) {
        EscortGrade[] grades = EscortGrade.values();
        int currentIndex = grade.ordinal();
        int loweredIndex = Math.max(0, currentIndex - steps);

        return grades[loweredIndex];
    }
}
