package com.back.nbe12142team06.domain.report.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "report")
class Report(
    application: Application,
    title: String,
    department: Department,
    purpose: String,
    originContent: String,
    notes: String? = null,
) : BaseTimeEntity() {  // createdAt, updatedAt 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null  // PK (BIGINT AUTO_INCREMENT)
        protected set

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    var application: Application = application  // 지원서 (FK → Application, UNIQUE / 동행 1건당 보고서 1개)
        protected set

    // 화면에 제목 입력칸이 없어 서비스에서 자동 생성한다. (예: "9월 22일 정형외과 진료 결과")
    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var department: Department = department  // 진료 과목
        protected set

    @Column(nullable = false, length = 200)
    var purpose: String = purpose  // 진료 목적 (한 줄)
        protected set

    // @Lob 은 PostgreSQL 에서 oid(대용량 객체)로 만들어져 트랜잭션 밖에서 읽으면 오류가 날 수 있어서,
    // 긴 문자열은 TEXT 컬럼으로 직접 지정한다.
    @Column(name = "origin_content", nullable = false, columnDefinition = "TEXT")
    var originContent: String = originContent  // 동행인이 작성한 진료 내용 본문 (원문)
        protected set

    @Column(columnDefinition = "TEXT")
    var notes: String? = notes  // 특이사항 (선택)
        protected set

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    var aiSummary: String? = null  // LLM 요약본 (JSON 문자열, nullable / 요약 완료 후 채워짐)
        protected set

    @Column(name = "summarized_at")
    var summarizedAt: LocalDateTime? = null  // AI 요약 완료 시각 (nullable)
        protected set

    /** AI 요약 완료 시 호출 (JSON 문자열 저장) */
    fun applySummary(aiSummary: String) {
        this.aiSummary = aiSummary
        this.summarizedAt = LocalDateTime.now()
    }

    companion object {
        /**
         * Java 호출부(테스트 등) 호환용. Lombok @Builder 가 없어져서 같은 모양으로 남겨 둔다.
         * 호출하는 쪽이 모두 코틀린이 되면 이름 있는 인자 생성자로 바꾸고 이 빌더는 지운다.
         */
        @JvmStatic
        fun builder(): ReportBuilder = ReportBuilder()
    }

    class ReportBuilder {
        private var application: Application? = null
        private var title: String? = null
        private var department: Department? = null
        private var purpose: String? = null
        private var originContent: String? = null
        private var notes: String? = null

        fun application(application: Application) = apply { this.application = application }

        fun title(title: String) = apply { this.title = title }

        fun department(department: Department) = apply { this.department = department }

        fun purpose(purpose: String) = apply { this.purpose = purpose }

        fun originContent(originContent: String) = apply { this.originContent = originContent }

        fun notes(notes: String?) = apply { this.notes = notes }

        fun build(): Report = Report(
            application = requireNotNull(application) { "application 은 필수입니다." },
            title = requireNotNull(title) { "title 은 필수입니다." },
            department = requireNotNull(department) { "department 는 필수입니다." },
            purpose = requireNotNull(purpose) { "purpose 는 필수입니다." },
            originContent = requireNotNull(originContent) { "originContent 는 필수입니다." },
            notes = notes,
        )
    }
}
