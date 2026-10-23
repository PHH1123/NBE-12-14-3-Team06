package com.back.nbe12142team06.domain.post.entity;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.global.crypto.PiiConverter;
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity;
import com.back.nbe12142team06.global.exception.InvalidException;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@Table(name = "post")
@SQLDelete(sql = "UPDATE post SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")  // 삭제 시 deleted_at 세팅 (소프트 딜리트)
@SQLRestriction("deleted_at IS NULL")   // 조회 시 deleted_at IS NULL 조건 자동 추가
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용 기본 생성자 (외부 직접 생성 불가)
@AllArgsConstructor(access = AccessLevel.PRIVATE)    // @Builder 전용 생성자
public class Post extends BaseSoftDeleteTimeEntity {  // createdAt, updatedAt, deletedAt 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // PK (BIGINT AUTO_INCREMENT)

    // ── 연관 관계 ──────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;  // 의뢰인 (FK → User)

    // ── 공고 기본 정보 ──────────────────────────
    @Column(nullable = false)
    private String title;  // 공고 제목

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // 공고 상세 내용 (TEXT)

    @Convert(converter = PiiConverter.class)
    @Column(name = "patient_note", columnDefinition = "TEXT")
    private String patientNote;  // 환자 특이사항

    @Column(nullable = false, length = 50)
    private String region;  // 지역 (예: 서울, 부산)

    // ── 병원 정보 ───────────────────────────────
    @Column(name = "hospital_name", nullable = false)
    private String hospitalName;  // 병원 이름

    @Column(name = "hospital_address", nullable = false)
    private String hospitalAddress;  // 병원 주소

    //프론트에서 받아올 예정
    @Column(name = "hospital_lat", precision = 10, scale = 7, nullable = false)
    private BigDecimal hospitalLat;  // 병원 위도 (예: 37.5665351) - 소수점 정밀도 위해 BigDecimal

    @Column(name = "hospital_lng", precision = 10, scale = 7, nullable = false)
    private BigDecimal hospitalLng;  // 병원 경도 (예: 126.9780000)

    // ── 픽업 정보 ───────────────────────────────
    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;  // 픽업 장소 주소

    @Column(name = "pickup_lat", precision = 10, scale = 7, nullable = false)
    private BigDecimal pickupLat;  // 픽업 위도

    @Column(name = "pickup_lng", precision = 10, scale = 7, nullable = false)
    private BigDecimal pickupLng;  // 픽업 경도

    // ── 일정 및 급여 ────────────────────────────
    @Min(value = 1, message = "시급은 0보다 커야 합니다.")
    @Column(name = "hourly_pay", nullable = false)
    private int hourlyPay;  // 시급 (CHECK > 0)

    @Column(name = "escort_start_at", nullable = false)
    private LocalDateTime escortStartAt;  // 동행 시작 예정 시간

    @Column(name = "escort_end_at", nullable = false)
    private LocalDateTime escortEndAt;  // 동행 종료 예정 시간

    @Column(name = "recruit_start_at", nullable = false)
    private LocalDateTime recruitStartAt;  // 모집 시작 시간

    @Column(name = "recruit_end_at", nullable = false)
    private LocalDateTime recruitEndAt;  // 모집 마감 시간

    // ── 상태 ────────────────────────────────────
    @Enumerated(EnumType.STRING)  // DB에 ENUM 문자열로 저장 (예: "OPEN")
    @Column(name = "post_status", nullable = false)
    @Builder.Default
    private PostStatus postStatus = PostStatus.OPEN;  // 기본값: OPEN

    @Column(name = "report_required", nullable = false)
    @Builder.Default
    private boolean reportRequired = true;  // 동행 후 보고서 작성 여부 (기본값: true)

    public void modify(
            String title, String content, String region,
            String hospitalName, String hospitalAddress,
            BigDecimal hospitalLat, BigDecimal hospitalLng,
            String pickupAddress, BigDecimal pickupLat, BigDecimal pickupLng,
            int hourlyPay,
            LocalDateTime recruitStartAt, LocalDateTime recruitEndAt,
            LocalDateTime escortStartAt, LocalDateTime escortEndAt,
            String patientNote, boolean reportRequired
    ) {
        this.title = title;
        this.content = content;
        this.region = region;
        this.hospitalName = hospitalName;
        this.hospitalAddress = hospitalAddress;
        this.hospitalLat = hospitalLat;
        this.hospitalLng = hospitalLng;
        this.pickupAddress = pickupAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.hourlyPay = hourlyPay;
        this.recruitStartAt = recruitStartAt;
        this.recruitEndAt = recruitEndAt;
        this.escortStartAt = escortStartAt;
        this.escortEndAt = escortEndAt;
        this.patientNote = patientNote;
        this.reportRequired = reportRequired;
    }

    // 모집 시작,마감 시간 포함하여 모집 기간인지 확인
    public boolean isWithinPeriod(LocalDateTime now){
        return !now.isBefore(recruitStartAt) && !now.isAfter(recruitEndAt);
    }

    // 동행 시간(시간 단위, 소수점 1자리) = 동행 종료 - 동행 시작
    public BigDecimal getEscortHours() {
        //2시간30분인경우 2.5시간 * 10,000 = 25,000
        //2시간35분인경우 2.6시간 * 10,000 = 26,000
        long minutes = Duration.between(escortStartAt, escortEndAt).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    // 총 지급액 = 시급 * 동행 시간
    public BigDecimal getTotalPay() {
        return getEscortHours().multiply(BigDecimal.valueOf(hourlyPay));
    }

    // 전이규칙 메서드
    private void changeStatus(PostStatus next) {
        if (!this.postStatus.canTransitionTo(next)) {
            throw new InvalidException(37, "'%s' 상태의 공고는 '%s' 상태로 바꿀 수 없습니다."
                    .formatted(this.postStatus.getDescription(), next.getDescription()));
        }
        this.postStatus = next;
    }

    // 매칭 완료 처리 (OPEN -> MATCHED)
    public void match() {
        changeStatus(PostStatus.MATCHED);
    }
    // 매칭된 공고 동행진행중 + 실제동행시작시간 update 처리
    public void startProgress(LocalDateTime departedAt) {
        // 출발 기록 때 이미 동행 진행 중이 된 공고에 완료 처리 쪽에서 한 번 더 부르므로, 이미 진행 중이면 상태는 두고 시각만 반영한다
        if (this.postStatus != PostStatus.IN_PROGRESS) {
            changeStatus(PostStatus.IN_PROGRESS);
        }
        this.escortStartAt = departedAt;
    }
    // 매칭된 공고 동행완료 + 실제동행마감시간 update 처리
    public void complete(LocalDateTime arrivedAt) {
        changeStatus(PostStatus.COMPLETED);
        this.escortEndAt = arrivedAt;
    }
    // 매칭된 공고 취소 처리 (MATCHED -> CANCELED)
    public void matchedCancel() {
        changeStatus(PostStatus.CANCELED);
    }
    // 공고 취소 처리 (OPEN, MATCHED -> CANCELED). 회원 탈퇴·정지 등으로 공고를 취소할 때 쓴다.
    public void cancel() {
        changeStatus(PostStatus.CANCELED);
    }
    // 모집마감시간 초과 처리 (OPEN -> EXPIRED)
    public void expire() {
        changeStatus(PostStatus.EXPIRED);
    }
    // 매칭 후 동행인 취소 시 공고 재오픈
    public void reopen() {
        changeStatus(PostStatus.OPEN);
    }
}