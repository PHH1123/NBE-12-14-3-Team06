package com.back.nbe12142team06.domain.report.entity;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@Table(name = "report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용 기본 생성자 (외부 직접 생성 불가)
@AllArgsConstructor(access = AccessLevel.PRIVATE)    // @Builder 전용 생성자
public class Report extends BaseTimeEntity {  // createdAt, updatedAt 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // PK (BIGINT AUTO_INCREMENT)

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;  // 지원서 (FK → Application, UNIQUE / 동행 1건당 보고서 1개)

    // 화면에 제목 입력칸이 없어 서비스에서 자동 생성한다. (예: "9월 22일 정형외과 진료 결과")
    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Department department;  // 진료 과목

    @Column(nullable = false, length = 200)
    private String purpose;  // 진료 목적 (한 줄)

    // @Lob 은 PostgreSQL 에서 oid(대용량 객체)로 만들어져 트랜잭션 밖에서 읽으면 오류가 날 수 있어서,
    // 긴 문자열은 TEXT 컬럼으로 직접 지정한다.
    @Column(name = "origin_content", nullable = false, columnDefinition = "TEXT")
    private String originContent;  // 동행인이 작성한 진료 내용 본문 (원문)

    @Column(columnDefinition = "TEXT")
    private String notes;  // 특이사항 (선택)

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;  // LLM 요약본 (JSON 문자열, nullable / 요약 완료 후 채워짐)

    @Column(name = "summarized_at")
    private LocalDateTime summarizedAt;  // AI 요약 완료 시각 (nullable)

    /** AI 요약 완료 시 호출 (JSON 문자열 저장) */
    public void applySummary(String aiSummary) {
        this.aiSummary = aiSummary;
        this.summarizedAt = LocalDateTime.now();
    }
}