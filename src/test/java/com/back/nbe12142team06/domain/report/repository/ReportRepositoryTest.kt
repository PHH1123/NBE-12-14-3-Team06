package com.back.nbe12142team06.domain.report.repository

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.report.entity.Department
import com.back.nbe12142team06.domain.report.entity.Report
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportRepositoryTest {

    @Autowired
    private lateinit var reportRepository: ReportRepository

    @Autowired
    private lateinit var em: EntityManager

    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        val client = User(
            "client1", "pw", "client1@test.com", "김의뢰",
            Role.CLIENT, Gender.FEMALE, LocalDate.of(1950, 1, 1), "01011112222", "서울",
        )
        val escort = User(
            "escort1", "pw", "escort1@test.com", "박동행",
            Role.ESCORT, Gender.MALE, LocalDate.of(1995, 1, 1), "01033334444", "서울",
        )
        em.persist(client)
        em.persist(escort)

        val post = Post(
            client = client,
            title = "정형외과 동행",
            content = "어머니 정기 진료 동행해주실 분",
            region = "서울",
            hospitalName = "서울정형외과",
            hospitalAddress = "서울시 은평구",
            hospitalLat = BigDecimal("37.6176000"),
            hospitalLng = BigDecimal("126.9227000"),
            pickupAddress = "서울시 은평구 자택",
            pickupLat = BigDecimal("37.6180000"),
            pickupLng = BigDecimal("126.9230000"),
            hourlyPay = 15000,
            recruitStartAt = LocalDateTime.now(),
            recruitEndAt = LocalDateTime.now().plusHours(12),
            escortStartAt = LocalDateTime.now().plusDays(1),
            escortEndAt = LocalDateTime.now().plusDays(1).plusHours(3),
        )
        em.persist(post)

        application = Application(post, escort)
        em.persist(application)
    }

    @Test
    @DisplayName("보고서를 저장하면 생성 시각이 자동으로 채워진다")
    fun 보고서_저장() {
        val report = Report(
            application = application,
            title = "진료 결과",
            department = Department.ORTHOPEDICS,
            purpose = "무릎 통증 검사",
            originContent = "정형외과 진료 후 물리치료 처방을 받으셨습니다.",
            notes = "대기 시간이 길어 잠시 앉아 쉬셨습니다.",
        )

        val saved = reportRepository.save(report)

        assertThat(saved.id).isNotNull()
        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.department).isEqualTo(Department.ORTHOPEDICS)
        assertThat(saved.aiSummary).isNull()  // 요약 전이므로 null
    }

    @Test
    @DisplayName("특이사항 없이도 보고서를 저장할 수 있다")
    fun 보고서_저장_특이사항_없음() {
        val report = Report(
            application = application,
            title = "진료 결과",
            department = Department.INTERNAL_MEDICINE,
            purpose = "정기 검진",
            originContent = "혈압과 혈당 수치를 확인했습니다.",
        )

        val saved = reportRepository.save(report)

        assertThat(saved.id).isNotNull()
        assertThat(saved.notes).isNull()  // 특이사항은 선택 입력
    }

    @Test
    @DisplayName("같은 동행 건에 보고서를 두 번 저장하면 예외가 발생한다")
    fun 보고서_중복_저장_예외() {
        reportRepository.save(
            Report(
                application = application,
                title = "첫 번째",
                department = Department.ORTHOPEDICS,
                purpose = "무릎 통증 검사",
                originContent = "내용",
            ),
        )
        em.flush()

        val duplicate = Report(
            application = application,
            title = "두 번째",
            department = Department.ORTHOPEDICS,
            purpose = "무릎 통증 검사",
            originContent = "내용",
        )

        assertThatThrownBy {
            reportRepository.save(duplicate)
            em.flush()  // flush 해야 DB 제약이 실제로 걸림
        }.isInstanceOf(Exception::class.java)
    }
}
