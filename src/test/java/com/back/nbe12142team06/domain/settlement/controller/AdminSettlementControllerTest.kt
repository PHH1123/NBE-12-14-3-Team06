package com.back.nbe12142team06.domain.settlement.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.client.SettlementClientResponse
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 관리자 정산 API 테스트.
 * 통계·목록 조회는 생성 시각(createdAt) 기준이라, 다른 테스트가 남긴 정산과 섞이지 않도록
 * 네이티브 쿼리로 생성 시각을 과거로 밀고 그 구간만 조회한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminSettlementControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc
    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var passwordEncoder: PasswordEncoder

    @field:MockitoBean private lateinit var settlementClient: SettlementClient
    @field:MockitoBean private lateinit var noticeService: NoticeService

    private lateinit var adminCookie: Cookie
    private lateinit var escortCookie: Cookie

    // 조회 구간: 20일 전 ~ 10일 전
    private val rangeStart: LocalDate = LocalDate.now().minusDays(20)
    private val rangeEnd: LocalDate = LocalDate.now().minusDays(10)

    private var completedSettlementId: Long = 0   // 정산 완료 135,000 (수수료 15,000)
    private var pendingSettlementId: Long = 0     // 정산 전 90,000
    private var recentSettlementId: Long = 0      // 생성 시각이 현재인 건 (기본 조회 구간 안)

    @BeforeEach
    fun init() {
        userRepository.save(createUser("adminSettle", Role.ADMIN, "010-6666-0001"))
        val client = userRepository.save(createUser("clientSettle", Role.CLIENT, "010-6666-0002"))
        val escort = userRepository.save(createUser("escortSettle", Role.ESCORT, "010-6666-0003"))
        escortProfileRepository.save(
            EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-123")
        )

        completedSettlementId = saveSettlement(
            client, escort, BigDecimal.valueOf(135_000), BigDecimal.valueOf(15_000), SettlementStatus.COMPLETED
        )
        pendingSettlementId = saveSettlement(
            client, escort, BigDecimal.valueOf(90_000), BigDecimal.valueOf(10_000), SettlementStatus.PENDING
        )
        recentSettlementId = saveSettlement(
            client, escort, BigDecimal.valueOf(45_000), BigDecimal.valueOf(5_000), SettlementStatus.PENDING
        )

        em.flush()
        // 생성 시각은 Auditing 이 넣으므로, 조회 구간 안으로 옮기려면 네이티브 쿼리로 바꿔야 한다
        moveCreatedAt(completedSettlementId, LocalDateTime.now().minusDays(15))
        moveCreatedAt(pendingSettlementId, LocalDateTime.now().minusDays(14))
        em.clear()

        adminCookie = login("adminSettle")
        escortCookie = login("escortSettle")
    }

    private fun moveCreatedAt(settlementId: Long, createdAt: LocalDateTime) {
        em.createNativeQuery("update settlement set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", settlementId)
            .executeUpdate()
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User {
        return User(
            username, passwordEncoder.encode("testPassword"), "$username@test.test",
            if (role == Role.ESCORT) "동행매니저" else "김춘식",
            role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
        )
    }

    private fun saveSettlement(
        client: User,
        escort: User,
        payoutAmount: BigDecimal,
        platformFee: BigDecimal,
        status: SettlementStatus
    ): Long {
        val post = postRepository.save(createPost(client))
        val application = applicationRepository.save(
            Application(
                post = post,
                escort = escort,
                status = ApplicationStatus.ACCEPTED,
                acceptedPostId = post.id,
            )
        )
        return settlementRepository.save(
            Settlement(
                id = null,
                payoutAmount = payoutAmount,
                platformFee = platformFee,
                penaltyAmount = BigDecimal.ZERO,
                settlementStatus = status,
                settledDate = LocalDate.now(),
                application = application,
                escort = escort
            )
        ).requiredId
    }

    private fun createPost(client: User): Post {
        return Post(
            title = "정형외과 동행 구합니다",
            content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
            region = "서울",
            hospitalName = "서울성모병원",
            hospitalAddress = "서울 서초구 반포대로 222",
            hospitalLat = BigDecimal.valueOf(37.5012743),
            hospitalLng = BigDecimal.valueOf(127.0051893),
            pickupAddress = "서울 서초구 잠원동 10-1",
            pickupLat = BigDecimal.valueOf(37.5160000),
            pickupLng = BigDecimal.valueOf(127.0200000),
            hourlyPay = 15_000,
            recruitStartAt = LocalDateTime.now().minusDays(30),
            recruitEndAt = LocalDateTime.now().minusDays(25),
            escortStartAt = LocalDateTime.now().minusDays(20),
            escortEndAt = LocalDateTime.now().minusDays(20).plusHours(10),
            postStatus = PostStatus.COMPLETED,
            client = client,
        )
    }

    private fun login(username: String): Cookie {
        return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "testPassword"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk())
            .andReturn()
            .response
            .getCookie("accessToken")!!
    }

    private fun LocalDate.param(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Test
    @DisplayName("[AdminSettlementController] 기간별 정산 통계 조회")
    fun getAnalytics() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/settlements/analytics")
                .cookie(adminCookie)
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        // 완료 135,000 + 미정산 90,000 = 225,000
        resultActions
            .andExpect(handler().handlerType(AdminSettlementController::class.java))
            .andExpect(handler().methodName("getAnalytics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-161"))
            .andExpect(jsonPath("$.msg").value("관리자 정산 통계 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.totalSettlementAmount").value(225_000))
            .andExpect(jsonPath("$.data.settlementCount").value(2))
            .andExpect(jsonPath("$.data.totalSettledAmount").value(135_000))
            .andExpect(jsonPath("$.data.settledCount").value(1))
            .andExpect(jsonPath("$.data.totalUnSettledAmount").value(90_000))
            .andExpect(jsonPath("$.data.unSettledCount").value(1))
            .andExpect(jsonPath("$.data.totalPlatformFee").value(15_000))
            .andExpect(jsonPath("$.data.errorSettlementCount").value(0))
    }

    @Test
    @DisplayName("[AdminSettlementController] 기간별 정산 목록 조회 - id 내림차순")
    fun getSettlements() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/settlements")
                .cookie(adminCookie)
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("getSettlements"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-162"))
            .andExpect(jsonPath("$.msg").value("관리자 정산 목록 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].id").value(pendingSettlementId))
            .andExpect(jsonPath("$.data.content[0].payoutAmount").value(90_000))
            .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.content[0].name").value("동행매니저"))
            .andExpect(jsonPath("$.data.content[1].id").value(completedSettlementId))
    }

    @Test
    @DisplayName("[AdminSettlementController] 기간별 정산 목록 조회 - 페이지 크기 적용")
    fun getSettlementsWithPaging() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/settlements")
                .cookie(adminCookie)
                .param("page", "0")
                .param("size", "1")
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(pendingSettlementId))
            .andExpect(jsonPath("$.data.totalElements").value(2))
    }

    @Test
    @DisplayName("[AdminSettlementController] 조회 기간을 생략하면 최근 7일만 조회한다")
    fun getSettlementsWithDefaultPeriod() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/settlements")
                .cookie(adminCookie)
        ).andDo(print())

        // 방금 만든 건은 들어오고, 생성 시각을 15일 전으로 옮긴 건은 빠진다
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.id == $recentSettlementId)]").exists())
            .andExpect(jsonPath("$.data.content[?(@.id == $completedSettlementId)]").isEmpty())
    }

    @Test
    @DisplayName("[AdminSettlementController] 관리자 정산 요청")
    fun requestSettlement() {
        doReturn(SettlementClientResponse("123-000000-123", "동행매니저", 90_000))
            .whenever(settlementClient).settlementRequest(any())

        val resultActions = mvc.perform(
            post("/api/v1/admin/settlements/$pendingSettlementId")
                .cookie(adminCookie)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("requestSettlement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-163"))
            .andExpect(jsonPath("$.msg").value("관리자 정산 요청이 완료되었습니다."))

        em.flush()
        em.clear()

        assertThat(settlementRepository.findById(pendingSettlementId).get().settlementStatus)
            .isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[AdminSettlementController] 관리자 정산 요청 - 이미 정산 완료된 건은 404-71")
    fun requestSettlementAlreadyCompleted() {
        val resultActions = mvc.perform(
            post("/api/v1/admin/settlements/$completedSettlementId")
                .cookie(adminCookie)
        ).andDo(print())

        // 정산 완료 건은 계좌 조회(findByIdAndState) 대상에서 빠진다
        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-71"))
            .andExpect(jsonPath("$.msg").value("정산 데이터를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminSettlementController] 동행인이 관리자 API 를 호출하면 403-1")
    fun forbiddenForNonAdmin() {
        mvc.perform(
            get("/api/v1/admin/settlements")
                .cookie(escortCookie)
        )
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[AdminSettlementController] 로그인 없이 관리자 API 를 호출하면 401-113")
    fun unauthorizedWithoutLogin() {
        mvc.perform(get("/api/v1/admin/settlements"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }
}
