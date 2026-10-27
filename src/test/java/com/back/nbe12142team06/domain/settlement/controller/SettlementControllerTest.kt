package com.back.nbe12142team06.domain.settlement.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.service.UserService
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
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
import java.util.UUID

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SettlementControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc

    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var postService: PostService
    @field:Autowired private lateinit var applicationService: ApplicationService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var settlementService: SettlementService
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository

    @field:MockitoBean private lateinit var noticeService: NoticeService
    @field:MockitoBean private lateinit var tossPaymentClient: TossPaymentClient

    private lateinit var savedUser1: User
    private lateinit var savedUser2: User
    private lateinit var savedUser3: User
    private var savedSettlement1Id: Long = 0
    private lateinit var savedPost1: Post
    private lateinit var accessTokenCookie2: Cookie
    private lateinit var accessTokenCookie3: Cookie

    @BeforeEach
    fun init() {
        val username = "testUsername"
        val password = "testPassword"
        val email = "testEmail@test.test"
        val name = "김춘식"
        val gender = "MALE"
        val birthDate = "1990-05-20"
        val phoneNum = "010-1234-5678"
        val region = "서울시"

        savedUser1 = userService.signUp(
            UserSignUpRequest(
                username, password, email, name, Role.CLIENT,
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                phoneNum, region
            )
        )

        savedUser2 = userService.signUp(
            UserSignUpRequest(
                username + "2", password + "2", email + "2", name + "2", Role.ESCORT,
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                "010-9999-9991", region
            )
        )

        savedUser3 = userService.signUp(
            UserSignUpRequest(
                username + "3", password + "3", email + "3", name + "3", Role.ESCORT,
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                "010-9999-9992", region
            )
        )

        escortProfileRepository.save(
            EscortProfile(savedUser2, "하이", "오픈은행", savedUser2.name, "000-1234567-000")
        )
        escortProfileRepository.save(
            EscortProfile(savedUser3, "으악", "오픈은행", savedUser3.name, "111-7654321-111")
        )

        // 교육 이수 처리 (지원 가능 상태) — 저장 후에 호출해야 DB 행이 존재함
        escortProfileRepository.verify(savedUser2.id, LocalDateTime.now())

        val title = "정형외과 동행 구합니다"
        val content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다."
        val postRegion = "서울"
        val hospitalName = "서울성모병원"
        val hospitalAddress = "서울 서초구 반포대로 222"
        val hospitalLat = BigDecimal.valueOf(37.5012743)
        val hospitalLng = BigDecimal.valueOf(127.0051893)
        val pickupAddress = "서울 서초구 잠원동 10-1"
        val pickupLat = BigDecimal.valueOf(37.5160000)
        val pickupLng = BigDecimal.valueOf(127.0200000)
        val hourlyPay = 15_000
        val recruitStartAt = LocalDateTime.now().plusDays(1)
        val recruitEndAt = LocalDateTime.now().plusDays(6)
        val escortStartAt = LocalDateTime.now().plusDays(7)
        val escortEndAt = LocalDateTime.now().plusDays(7).plusHours(4)

        val postWriteRequest1 = PostWriteRequest(
            title, content, postRegion, hospitalName, hospitalAddress, hospitalLat, hospitalLng,
            pickupAddress, pickupLat, pickupLng, hourlyPay, recruitStartAt, recruitEndAt, escortStartAt, escortEndAt,
            RideSelect.TAXI, RideSelect.TAXI, "", true
        )

        savedPost1 = postService.findById(postService.write(savedUser1.id, postWriteRequest1).id)
        paymentRepository.testStatusDoneAndPutPaymentKey(savedPost1.requiredId, UUID.randomUUID().toString())

        val postWriteRequest2 = PostWriteRequest(
            title + "2", content + "2", postRegion + "2", hospitalName + "2", hospitalAddress + "2",
            hospitalLat, hospitalLng, pickupAddress + "2", pickupLat, pickupLng, hourlyPay,
            recruitStartAt, recruitEndAt, escortStartAt, escortEndAt,
            RideSelect.TAXI, RideSelect.TAXI, "", true
        )

        val post2 = postService.write(savedUser1.id, postWriteRequest2)
        paymentRepository.testStatusDoneAndPutPaymentKey(post2.id, UUID.randomUUID().toString())

        // 영속성 컨텍스트 초기화 후 공고 다시 조회
        savedPost1 = postService.findById(savedPost1.requiredId)

        ReflectionTestUtils.setField(savedPost1, "recruitStartAt", LocalDateTime.now().minusMinutes(1))
        val applyResponse1 = applicationService.apply(savedPost1.requiredId, savedUser2.id)
        applicationService.accept(applyResponse1.id, savedUser1.id)

        val application1 = applicationRepository.findAllByPostIdWithEscort(savedPost1.requiredId).first()

        savedSettlement1Id = settlementService.createSettlement(
            savedPost1.totalPay, application1, savedUser2, LocalDate.now().plusDays(1)
        ).requiredId

        // user로 로그인해 인증 쿠키 확보
        accessTokenCookie2 = login(username + "2", password + "2")
        accessTokenCookie3 = login(username + "3", password + "3")
    }

    private fun login(username: String, password: String): Cookie {
        return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "$password"
                    }
                    """.trimIndent()
                )
        )
            .andReturn()
            .response
            .getCookie("accessToken")!!
    }

    @Test
    @DisplayName("[SettlementController] 정산 요청 - 성공")
    fun settlementReq() {
        // init()에서 이미 MATCHED 상태로 변경됨
        savedPost1.startProgress(LocalDateTime.now().minusHours(1))
        savedPost1.complete(LocalDateTime.now()) //post.complete() 변경으로 now() 추가

        val resultActions = mvc.perform(
            post("/api/v1/settlements/$savedSettlement1Id")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementRequest"))
            .andExpect(jsonPath("$.statusCode").value("200-71"))
            .andExpect(jsonPath("$.msg").value("정산에 성공했습니다."))

        val settlement: Settlement = settlementRepository.findById(savedSettlement1Id).get()
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[SettlementController] 정산 요청 - 실패 찾을 수 없음")
    fun settlementReqFailNotFound() {
        val settlementId = 10000L

        val resultActions = mvc.perform(
            post("/api/v1/settlements/$settlementId")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementRequest"))
            .andExpect(jsonPath("$.statusCode").value("404-71"))
            .andExpect(jsonPath("$.msg").value("정산 데이터를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[SettlementController] 정산 목록 조회 - 성공")
    fun settlementList() {
        val resultActions = mvc.perform(
            get("/api/v1/settlements")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        // 목록은 Page 라서 data.content 에 담긴다 (예전에는 data[0] 을 봐서 항상 통과했다)
        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementList"))
            .andExpect(jsonPath("$.statusCode").value("200-72"))
            .andExpect(jsonPath("$.msg").value("정산 목록을 가져왔습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(savedSettlement1Id))
    }

    @Test
    @DisplayName("[SettlementController] 정산 상세 조회 - 성공")
    fun settlementDetail() {
        val resultActions = mvc.perform(
            get("/api/v1/settlements/$savedSettlement1Id")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementDetail"))
            .andExpect(jsonPath("$.statusCode").value("200-73"))
            .andExpect(jsonPath("$.msg").value("정산 상세 데이터를 조회했습니다."))
            .andExpect(jsonPath("$.data").exists())
    }

    @Test
    @DisplayName("[SettlementController] 정산 상세 조회 - 실패 찾을 수 없음")
    fun settlementDetailFailNotFound() {
        val settlementId = 10000L

        val resultActions = mvc.perform(
            get("/api/v1/settlements/$settlementId")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementDetail"))
            .andExpect(jsonPath("$.statusCode").value("404-71"))
            .andExpect(jsonPath("$.msg").value("정산 데이터를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[SettlementController] 정산 상세 조회 - 실패 권한 부족")
    fun settlementDetailFailForbidden() {
        val resultActions = mvc.perform(
            get("/api/v1/settlements/$savedSettlement1Id")
                .cookie(accessTokenCookie3)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(SettlementController::class.java))
            .andExpect(handler().methodName("settlementDetail"))
            .andExpect(jsonPath("$.statusCode").value("403-71"))
            .andExpect(jsonPath("$.msg").value("정산 요청할 권한이 없습니다."))
    }

    @Test
    @DisplayName("[SettlementService] 자동 정산 - 성공")
    fun settlementScheduler() {
        val application = applicationRepository.save(
            Application(post = savedPost1, escort = savedUser3, status = ApplicationStatus.REJECTED)
        )

        val settlement = settlementRepository.save(
            Settlement(
                id = null,
                payoutAmount = BigDecimal.valueOf(1000),
                platformFee = BigDecimal.ZERO,
                penaltyAmount = BigDecimal.ZERO,
                settlementStatus = SettlementStatus.PENDING,
                settledDate = LocalDate.now(),
                application = application,
                escort = savedUser2
            )
        )

        val counts = settlementService.settlementProcess()

        val savedSettlement = settlementRepository.findById(settlement.requiredId).get()

        assertThat(savedSettlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(1)
        assertThat(counts[2]).isEqualTo(0)
    }

    // savedUser2 의 정산 건을 하나 더 만든다 (목록 페이징·기간 필터 확인용)
    private fun saveExtraSettlement(settledDate: LocalDate): Long {
        val application = applicationRepository.save(
            Application(post = savedPost1, escort = savedUser2, status = ApplicationStatus.REJECTED)
        )
        return settlementRepository.save(
            Settlement(
                id = null,
                payoutAmount = BigDecimal.valueOf(90_000),
                platformFee = BigDecimal.valueOf(10_000),
                penaltyAmount = BigDecimal.ZERO,
                settlementStatus = SettlementStatus.PENDING,
                settledDate = settledDate,
                application = application,
                escort = savedUser2
            )
        ).requiredId
    }

    @Test
    @DisplayName("[SettlementController] 정산 목록 조회 - 페이지 크기와 정렬 적용")
    fun settlementListWithPaging() {
        saveExtraSettlement(LocalDate.now())

        val resultActions = mvc.perform(
            get("/api/v1/settlements")
                .cookie(accessTokenCookie2)
                .param("page", "0")
                .param("size", "1")
                .param("sort", "DESC")
        ).andDo(print())

        // 정산일 내림차순이라 예정일이 내일인 init() 정산이 먼저 온다
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(savedSettlement1Id))
            .andExpect(jsonPath("$.data.totalElements").value(2))
    }

    @Test
    @DisplayName("[SettlementController] 정산 목록 조회 - 조회 기간을 벗어난 정산은 빠진다")
    fun settlementListWithPeriod() {
        val oldSettlementId = saveExtraSettlement(LocalDate.now().minusDays(10))

        val resultActions = mvc.perform(
            get("/api/v1/settlements")
                .cookie(accessTokenCookie2)
                .param("startDate", LocalDate.now().minusDays(15).toString())
                .param("endDate", LocalDate.now().minusDays(5).toString())
        ).andDo(print())

        // 10일 전 정산만 들어오고, 예정일이 내일인 정산은 빠진다
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(oldSettlementId))
    }

    @Test
    @DisplayName("[SettlementController] 의뢰인이 정산 목록을 조회하면 403-1")
    fun settlementListWithClientRole() {
        val clientCookie = login("testUsername", "testPassword")

        mvc.perform(
            get("/api/v1/settlements")
                .cookie(clientCookie)
        )
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[SettlementController] 로그인 없이 정산 목록을 조회하면 401-113")
    fun settlementListWithoutLogin() {
        mvc.perform(get("/api/v1/settlements"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }
}
