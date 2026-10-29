package com.back.nbe12142team06.domain.post.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.crypto.PiiConverter
import com.back.nbe12142team06.global.entity.BaseSoftDeleteTimeEntity
import com.back.nbe12142team06.global.exception.InvalidException
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.Min
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(name = "post")
@SQLDelete(sql = "UPDATE post SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")  // 삭제 시 deleted_at 세팅 (소프트 딜리트)
@SQLRestriction("deleted_at IS NULL")   // 조회 시 deleted_at IS NULL 조건 자동 추가
class Post internal constructor() : BaseSoftDeleteTimeEntity() {  // createdAt, updatedAt, deletedAt 상속 / JPA용 기본 생성자

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null  // PK (BIGINT AUTO_INCREMENT)
        protected set

    // ── 연관 관계 ──────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    lateinit var client: User  // 의뢰인 (FK → User)
        protected set

    // ── 공고 기본 정보 ──────────────────────────
    @Column(nullable = false)
    lateinit var title: String  // 공고 제목
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var content: String  // 공고 상세 내용 (TEXT)
        protected set

    @Convert(converter = PiiConverter::class)
    @Column(name = "patient_note", columnDefinition = "TEXT")
    var patientNote: String? = null  // 환자 특이사항
        protected set

    @Column(nullable = false, length = 50)
    lateinit var region: String  // 지역 (예: 서울, 부산)
        protected set

    // ── 병원 정보 ───────────────────────────────
    @Column(name = "hospital_name", nullable = false)
    lateinit var hospitalName: String  // 병원 이름
        protected set

    @Column(name = "hospital_address", nullable = false)
    lateinit var hospitalAddress: String  // 병원 주소
        protected set

    //프론트에서 받아올 예정
    @Column(name = "hospital_lat", precision = 10, scale = 7, nullable = false)
    lateinit var hospitalLat: BigDecimal  // 병원 위도 (예: 37.5665351) - 소수점 정밀도 위해 BigDecimal
        protected set

    @Column(name = "hospital_lng", precision = 10, scale = 7, nullable = false)
    lateinit var hospitalLng: BigDecimal  // 병원 경도 (예: 126.9780000)
        protected set

    // ── 픽업 정보 ───────────────────────────────
    @Column(name = "pickup_address", nullable = false)
    lateinit var pickupAddress: String  // 픽업 장소 주소
        protected set

    @Column(name = "pickup_lat", precision = 10, scale = 7, nullable = false)
    lateinit var pickupLat: BigDecimal  // 픽업 위도
        protected set

    @Column(name = "pickup_lng", precision = 10, scale = 7, nullable = false)
    lateinit var pickupLng: BigDecimal  // 픽업 경도
        protected set

    // ── 일정 및 급여 ────────────────────────────
    @field:Min(value = 1, message = "시급은 0보다 커야 합니다.")
    @Column(name = "hourly_pay", nullable = false)
    var hourlyPay: Int = 0  // 시급 (CHECK > 0)
        protected set

    @Column(name = "escort_start_at", nullable = false)
    lateinit var escortStartAt: LocalDateTime  // 동행 시작 예정 시간
        protected set

    @Column(name = "escort_end_at", nullable = false)
    lateinit var escortEndAt: LocalDateTime  // 동행 종료 예정 시간
        protected set

    @Column(name = "recruit_start_at", nullable = false)
    lateinit var recruitStartAt: LocalDateTime  // 모집 시작 시간
        protected set

    @Column(name = "recruit_end_at", nullable = false)
    lateinit var recruitEndAt: LocalDateTime  // 모집 마감 시간
        protected set

    // ── 상태 ────────────────────────────────────
    @Enumerated(EnumType.STRING)  // DB에 ENUM 문자열로 저장 (예: "OPEN")
    @Column(name = "post_status", nullable = false)
    var postStatus: PostStatus = PostStatus.OPEN  // 기본값: OPEN
        protected set

    @Column(name = "report_required", nullable = false)
    var reportRequired: Boolean = true  // 동행 후 보고서 작성 여부 (기본값: true)
        protected set

    // 코틀린에서 공고를 만들 때 쓴다. 필수 값을 빠뜨리면 컴파일이 안 된다.
    constructor(
        client: User,
        title: String,
        content: String,
        region: String,
        hospitalName: String,
        hospitalAddress: String,
        hospitalLat: BigDecimal,
        hospitalLng: BigDecimal,
        pickupAddress: String,
        pickupLat: BigDecimal,
        pickupLng: BigDecimal,
        hourlyPay: Int,
        recruitStartAt: LocalDateTime,
        recruitEndAt: LocalDateTime,
        escortStartAt: LocalDateTime,
        escortEndAt: LocalDateTime,
        patientNote: String? = null,
        reportRequired: Boolean = true,
        id: Long? = null,
        postStatus: PostStatus = PostStatus.OPEN,
    ) : this() {
        this.client = client
        this.title = title
        this.content = content
        this.region = region
        this.hospitalName = hospitalName
        this.hospitalAddress = hospitalAddress
        this.hospitalLat = hospitalLat
        this.hospitalLng = hospitalLng
        this.pickupAddress = pickupAddress
        this.pickupLat = pickupLat
        this.pickupLng = pickupLng
        this.hourlyPay = hourlyPay
        this.recruitStartAt = recruitStartAt
        this.recruitEndAt = recruitEndAt
        this.escortStartAt = escortStartAt
        this.escortEndAt = escortEndAt
        this.patientNote = patientNote
        this.reportRequired = reportRequired
        this.id = id
        this.postStatus = postStatus
    }

    fun modify(
        title: String, content: String, region: String,
        hospitalName: String, hospitalAddress: String,
        hospitalLat: BigDecimal, hospitalLng: BigDecimal,
        pickupAddress: String, pickupLat: BigDecimal, pickupLng: BigDecimal,
        hourlyPay: Int,
        recruitStartAt: LocalDateTime, recruitEndAt: LocalDateTime,
        escortStartAt: LocalDateTime, escortEndAt: LocalDateTime,
        patientNote: String?, reportRequired: Boolean,
    ) {
        this.title = title
        this.content = content
        this.region = region
        this.hospitalName = hospitalName
        this.hospitalAddress = hospitalAddress
        this.hospitalLat = hospitalLat
        this.hospitalLng = hospitalLng
        this.pickupAddress = pickupAddress
        this.pickupLat = pickupLat
        this.pickupLng = pickupLng
        this.hourlyPay = hourlyPay
        this.recruitStartAt = recruitStartAt
        this.recruitEndAt = recruitEndAt
        this.escortStartAt = escortStartAt
        this.escortEndAt = escortEndAt
        this.patientNote = patientNote
        this.reportRequired = reportRequired
    }

    val requiredId : Long
        get() = id ?: error("ID가 NULL입니다.")

    // 모집 시작,마감 시간 포함하여 모집 기간인지 확인
    fun isWithinPeriod(now: LocalDateTime): Boolean =
        !now.isBefore(recruitStartAt) && !now.isAfter(recruitEndAt)

    // 동행 시간(시간 단위, 소수점 1자리) = 동행 종료 - 동행 시작
    val escortHours: BigDecimal
        get() {
            //2시간30분인경우 2.5시간 * 10,000 = 25,000
            //2시간35분인경우 2.6시간 * 10,000 = 26,000
            val minutes = Duration.between(escortStartAt, escortEndAt).toMinutes()
            return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP)
        }

    // 총 지급액 = 시급 * 동행 시간
    val totalPay: BigDecimal
        get() = escortHours.multiply(BigDecimal.valueOf(hourlyPay.toLong()))

    // 전이규칙 메서드
    private fun changeStatus(next: PostStatus) {
        if (!this.postStatus.canTransitionTo(next)) {
            throw InvalidException(37, "'${this.postStatus.description}' 상태의 공고는 '${next.description}' 상태로 바꿀 수 없습니다.")
        }
        this.postStatus = next
    }

    // 매칭 완료 처리 (OPEN -> MATCHED)
    fun match() {
        changeStatus(PostStatus.MATCHED)
    }

    // 매칭된 공고 동행진행중 + 실제동행시작시간 update 처리
    fun startProgress(departedAt: LocalDateTime) {
        // 출발 기록 때 이미 동행 진행 중이 된 공고에 완료 처리 쪽에서 한 번 더 부르므로, 이미 진행 중이면 상태는 두고 시각만 반영한다
        if (this.postStatus != PostStatus.IN_PROGRESS) {
            changeStatus(PostStatus.IN_PROGRESS)
        }
        this.escortStartAt = departedAt
    }

    // 매칭된 공고 동행완료 + 실제동행마감시간 update 처리
    fun complete(arrivedAt: LocalDateTime) {
        changeStatus(PostStatus.COMPLETED)
        this.escortEndAt = arrivedAt
    }

    // 매칭된 공고 취소 처리 (MATCHED -> CANCELED)
    fun matchedCancel() {
        changeStatus(PostStatus.CANCELED)
    }

    // 공고 취소 처리 (OPEN, MATCHED -> CANCELED). 회원 탈퇴·정지 등으로 공고를 취소할 때 쓴다.
    fun cancel() {
        changeStatus(PostStatus.CANCELED)
    }

    // 모집마감시간 초과 처리 (OPEN -> EXPIRED)
    fun expire() {
        changeStatus(PostStatus.EXPIRED)
    }

    // 매칭 후 동행인 취소 시 공고 재오픈
    fun reopen() {
        changeStatus(PostStatus.OPEN)
    }
}
