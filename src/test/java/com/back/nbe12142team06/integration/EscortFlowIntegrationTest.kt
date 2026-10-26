package com.back.nbe12142team06.integration

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.education.entity.EducationVideo
import com.back.nbe12142team06.domain.education.repository.EducationVideoRepository
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * [통합] 가입부터 정산까지 실제 HTTP 요청으로 전 과정을 검증한다.
 *
 * 이 클래스에는 의도적으로 @Transactional을 붙이지 않는다. 각 HTTP 요청이 별도의
 * 트랜잭션을 종료한 뒤 open-in-view: false 환경에서 JSON 직렬화까지 확인하기 위함이다.
 * 테스트 사이에 데이터가 남으므로 가입 정보는 nanoTime을 기반으로 고유하게 생성한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EscortFlowIntegrationTest {

    @field:Autowired
    private lateinit var mvc: MockMvc

    @field:Autowired
    private lateinit var educationVideoRepository: EducationVideoRepository

    @field:Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @field:Autowired
    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    @field:Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @field:Autowired
    private lateinit var postRepository: PostRepository

    @field:Autowired
    private lateinit var paymentRepository: PaymentRepository

    @field:Autowired
    private lateinit var settlementRepository: SettlementRepository

    @field:Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @field:MockitoBean
    private lateinit var tossPaymentClient: TossPaymentClient

    @BeforeEach
    fun stubToss() {
        // 실제 토스 API를 호출하지 않는다. confirm/cancel 모두 스텁한다.
        given(tossPaymentClient.callApiConfirm(anyString(), anyString(), anyString()))
            .willReturn(ResponseEntity.ok(TossConfirmResponse("카드", "30000")))
        given(tossPaymentClient.callApiCancel(anyString(), anyString(), anyString()))
            .willReturn(ResponseEntity.ok(TossConfirmResponse("카드", "30000")))
    }

    // 공고 등록 후 지원 시점까지 시간이 흐른 상황을 재현한다.
    private fun beginRecruitmentForTest(postId: Long) {
        transactionTemplate.executeWithoutResult {
            val savedPost = postRepository.findById(postId).orElseThrow()
            ReflectionTestUtils.setField(savedPost, "recruitStartAt", LocalDateTime.now().minusMinutes(1))
        }
    }

    private fun tag(): String = System.nanoTime().toString()

    private fun phone(tag: String, seq: Int): String = "010${tag.substring(tag.length - 7)}$seq"

    private fun signUp(
        username: String,
        password: String,
        email: String,
        name: String,
        role: String,
        phoneNum: String,
    ): Long {
        val body = """
            {
                "username": "$username",
                "password": "$password",
                "email": "$email",
                "name": "$name",
                "role": "$role",
                "gender": "MALE",
                "birthDate": "1990-01-01",
                "phoneNum": "$phoneNum",
                "region": "서울"
            }
        """.trimIndent()

        val response = mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-1"))
            .andReturn().response.contentAsString

        return JsonPath.parse(response).read<Number>("$.data.id").toLong()
    }

    private fun login(username: String, password: String): Cookie =
        mvc.perform(
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
            .andExpect(status().isOk())
            .andReturn().response.getCookie("accessToken")!!

    private fun createClientProfile(clientCookie: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/client")
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "emergencyContactName": "보호자",
                        "emergencyContactPhone": "010-9999-0000",
                        "careNote": "특이사항 없음"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-2"))
    }

    private fun createEscortProfile(escortCookie: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/escort")
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "intro": "안전하게 동행하겠습니다.",
                        "bankName": "국민은행",
                        "accountHolder": "동행인",
                        "accountNumber": "123-456-7890"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-3"))
    }

    // 교육 자체가 검증 대상이 아닌 경우에 한해, 동행인 프로필을 직접 인증한다.
    private fun verifyEscortDirectly(escortId: Long) {
        transactionTemplate.executeWithoutResult {
            escortProfileRepository.verify(escortId, LocalDateTime.now())
        }
    }

    private fun postJson(
        recruitStartAt: LocalDateTime,
        recruitEndAt: LocalDateTime,
        escortStartAt: LocalDateTime,
        escortEndAt: LocalDateTime,
        hourlyPay: Int,
    ): String = """
        {
            "title": "정형외과 동행 구합니다",
            "content": "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
            "region": "서울",
            "hospitalName": "서울성모병원",
            "hospitalAddress": "서울 서초구 반포대로 222",
            "hospitalLat": 37.5012743,
            "hospitalLng": 127.0051893,
            "pickupAddress": "서울 서초구 잠원동 10-1",
            "pickupLat": 37.5160000,
            "pickupLng": 127.0200000,
            "hourlyPay": $hourlyPay,
            "recruitStartAt": "$recruitStartAt",
            "recruitEndAt": "$recruitEndAt",
            "escortStartAt": "$escortStartAt",
            "escortEndAt": "$escortEndAt",
            "rideSelectToHospital": "TAXI",
            "rideSelectToHome": "TAXI",
            "patientNote": "거동이 불편하신 70대 어르신",
            "reportRequired": true
        }
    """.trimIndent()

    private fun confirmPayment(paymentId: Long, clientCookie: Cookie, tag: String, plannedAmount: String) {
        mvc.perform(
            post("/api/v1/payments/{paymentId}/confirm", paymentId)
                .cookie(clientCookie)
                .sessionAttrs(mapOf("amount" to plannedAmount, "orderId" to "order-$tag"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"paymentKey": "test-payment-key-$tag", "orderId": "order-$tag", "amount": "$plannedAmount"}"""
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-41"))
    }

    // ── 시나리오 1 ─────────────────────────────────────────────

    @Test
    @DisplayName("[통합] 의뢰인 가입부터 정산 데이터 생성까지 전 과정이 동작한다")
    fun 의뢰인_가입부터_정산까지_전_과정() {
        val tag = tag()
        val hourlyPay = 15000

        // 1. 의뢰인 회원가입
        val clientUsername = "flow-client-$tag"
        val clientPassword = "testPassword"
        signUp(clientUsername, clientPassword, "flow-client-$tag@test.com", "의뢰인", "CLIENT", phone(tag, 1))

        // 2. 의뢰인 로그인 -> accessToken 쿠키 확보
        val clientCookie = login(clientUsername, clientPassword)

        // 3. 의뢰인 프로필 등록
        createClientProfile(clientCookie)

        // 4. 동행인 회원가입 -> 로그인 -> 프로필 등록
        val escortUsername = "flow-escort-$tag"
        val escortPassword = "testPassword"
        val escortUserId = signUp(escortUsername, escortPassword, "flow-escort-$tag@test.com", "동행인", "ESCORT", phone(tag, 2))
        val escortCookie = login(escortUsername, escortPassword)

        // 교육 영상 진행 상황은 프로필 생성 시 존재하는 영상들에 대해 생성되므로,
        // 프로필을 만들기 전에 필수 영상을 먼저 준비한다.
        val video = educationVideoRepository.save(
            EducationVideo("교육 영상", "https://example.com/video.mp4", 1, true)
        )
        createEscortProfile(escortCookie)

        // 5. 동행인 교육 이수 (실제 시청 기록 API 사용)
        mvc.perform(
            post("/api/v1/education-videos/{videoId}/watchlogs", video.id!!)
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"positionSec": 1.0}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-101"))

        assertThat(escortProfileRepository.findById(escortUserId).orElseThrow().verified).isTrue()

        // 6. 의뢰인이 공고 등록 (동행 예정 2시간)
        val now = LocalDateTime.now()
        val recruitStartAt = now.plusDays(1)
        val recruitEndAt = recruitStartAt.plusHours(1)
        val escortStartAt = recruitEndAt.plusHours(1)
        val escortEndAt = escortStartAt.plusHours(2)

        val writeResponse = mvc.perform(
            post("/api/v1/posts")
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(postJson(recruitStartAt, recruitEndAt, escortStartAt, escortEndAt, hourlyPay))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-11"))
            .andReturn().response.contentAsString

        val writeCtx = JsonPath.parse(writeResponse)
        val postId = writeCtx.read<Number>("$.data.id").toLong()
        val paymentId = writeCtx.read<Number>("$.data.paymentId").toLong()
        assertThat(postId).isNotNull()
        assertThat(paymentId).isNotNull()

        // READY 결제는 findByPostId에서 성공 결제로 조회되지 않으므로, 실제 승인 API를 거친다.
        val plannedAmount = (hourlyPay * 2).toString()
        confirmPayment(paymentId, clientCookie, tag, plannedAmount)

        // 7. 동행인 지원
        beginRecruitmentForTest(postId)
        val applyResponse = mvc.perform(
            post("/api/v1/applications/{postId}", postId).cookie(escortCookie)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-21"))
            .andReturn().response.contentAsString
        val applicationId = JsonPath.parse(applyResponse).read<Number>("$.data.id").toLong()

        // 8. 지원 목록에서 해당 동행인 확인
        mvc.perform(get("/api/v1/applications/posts/{postId}", postId).cookie(clientCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].applicationId").value(applicationId))
            .andExpect(jsonPath("$.data.content[0].escortId").value(escortUserId))

        // 9. 지원 승인 -> ACCEPTED / MATCHED
        mvc.perform(patch("/api/v1/applications/{applicationId}/accept", applicationId).cookie(clientCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
        assertThat(postRepository.findById(postId).orElseThrow().postStatus).isEqualTo(PostStatus.MATCHED)

        // 10. 동행 단계 순차 변경
        mvc.perform(
            patch("/api/v1/applications/{applicationId}/progress", applicationId)
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"progress": "DEPARTED"}""")
        ).andExpect(status().isOk())

        assertThat(postRepository.findById(postId).orElseThrow().postStatus).isEqualTo(PostStatus.IN_PROGRESS)

        // 순서를 어기면 400
        mvc.perform(
            patch("/api/v1/applications/{applicationId}/progress", applicationId)
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"progress": "AT_HOSPITAL"}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest())

        for (step in listOf(
            EscortProgress.TO_HOSPITAL, EscortProgress.AT_HOSPITAL,
            EscortProgress.TO_HOME, EscortProgress.ARRIVED_HOME,
        )) {
            mvc.perform(
                patch("/api/v1/applications/{applicationId}/progress", applicationId)
                    .cookie(escortCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"progress": "$step"}""")
            ).andExpect(status().isOk())
        }

        // 실제 시각 간격이 수 밀리초이므로 DEPARTED 로그만 2시간 전으로 옮긴다.
        // 계획/실제 동행 시간이 같은 경로에서 정산 금액을 검증하기 위한 테스트 데이터 조정이다.
        val application = applicationRepository.findById(applicationId).orElseThrow()
        val arrivedAt = escortProgressLogRepository
            .findByApplicationAndProgress(application, EscortProgress.ARRIVED_HOME)
            .orElseThrow().occurredAt
        val departedLog = escortProgressLogRepository
            .findByApplicationAndProgress(application, EscortProgress.DEPARTED)
            .orElseThrow()
        escortProgressLogRepository.delete(departedLog)
        escortProgressLogRepository.saveAndFlush(
            EscortProgressLog(
                application = application,
                progress = EscortProgress.DEPARTED,
                occurredAt = arrivedAt.minusHours(2),
            )
        )

        // 11. 의뢰인의 동행 완료 처리
        mvc.perform(patch("/api/v1/posts/{postId}/escortComplete", postId).cookie(clientCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-18"))

        assertThat(postRepository.findById(postId).orElseThrow().postStatus).isEqualTo(PostStatus.COMPLETED)
        assertThat(escortProfileRepository.findById(escortUserId).orElseThrow().completedCount).isEqualTo(1)

        val payments = paymentRepository.findSuccessPayByPostId(postId)
        assertThat(payments).hasSize(1)
        verify(tossPaymentClient, never()).callApiCancel(anyString(), anyString(), anyString())

        val totalPay = BigDecimal.TWO.multiply(BigDecimal.valueOf(hourlyPay.toLong()))
        val expectedPayout = totalPay.multiply(BigDecimal.valueOf(0.9))
        val expectedPlatformFee = totalPay.subtract(expectedPayout)
        val settlement = settlementRepository.findAll().stream()
            .filter { it.escort.id == escortUserId }
            .findFirst()
            .orElseThrow()
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.PENDING)
        assertThat(settlement.payoutAmount).isEqualByComparingTo(expectedPayout)
        assertThat(settlement.platformFee).isEqualByComparingTo(expectedPlatformFee)

        // 12. 동행인 진료 보고서 작성
        mvc.perform(
            post("/api/v1/applications/{applicationId}/report", applicationId)
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "department": "ORTHOPEDICS",
                        "purpose": "무릎 통증 검사",
                        "originContent": "무릎 통증으로 내원하셨고 물리치료 처방을 받으셨습니다."
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-51"))

        // 13. 의뢰인 리뷰 작성
        mvc.perform(
            post("/api/v1/applications/{applicationId}/reviews", applicationId)
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rating": 5}""")
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value("201-61"))

        val escortProfile = escortProfileRepository.findById(escortUserId).orElseThrow()
        assertThat(escortProfile.ratingCount).isEqualTo(1)
        assertThat(escortProfile.ratingSum).isEqualTo(5)

        // 14. 동행인 리뷰 목록 (LazyInitializationException 재발 방지 검증)
        mvc.perform(get("/api/v1/users/{userId}/reviews", escortUserId).cookie(escortCookie))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-61"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].rating").value(5))
            .andExpect(jsonPath("$.data[0].applicationId").value(applicationId))
    }

    // ── 시나리오 2 ─────────────────────────────────────────────

    @Test
    @DisplayName("[통합] 지원 승인 시 같은 공고의 나머지 지원자는 자동 거절되고 이후 지원은 막힌다")
    fun 승인되면_나머지_지원자는_자동_거절된다() {
        val tag = tag()
        val hourlyPay = 15000

        val clientUsername = "flow2-client-$tag"
        signUp(clientUsername, "testPassword", "flow2-client-$tag@test.com", "의뢰인", "CLIENT", phone(tag, 1))
        val clientCookie = login(clientUsername, "testPassword")

        val now = LocalDateTime.now()
        val writeResponse = mvc.perform(
            post("/api/v1/posts")
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    postJson(
                        now.plusDays(1), now.plusDays(1).plusHours(1),
                        now.plusDays(1).plusHours(2), now.plusDays(1).plusHours(4), hourlyPay,
                    )
                )
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val writeCtx = JsonPath.parse(writeResponse)
        val postId = writeCtx.read<Number>("$.data.id").toLong()
        val paymentId = writeCtx.read<Number>("$.data.paymentId").toLong()

        val plannedAmount = (hourlyPay * 2).toString()
        confirmPayment(paymentId, clientCookie, tag, plannedAmount)

        // 동행인 2명이 지원
        val escort1Username = "flow2-escort1-$tag"
        val escort1Id = signUp(escort1Username, "testPassword", "flow2-escort1-$tag@test.com", "동행인1", "ESCORT", phone(tag, 2))
        val escort1Cookie = login(escort1Username, "testPassword")
        createEscortProfile(escort1Cookie)
        verifyEscortDirectly(escort1Id)

        val escort2Username = "flow2-escort2-$tag"
        val escort2Id = signUp(escort2Username, "testPassword", "flow2-escort2-$tag@test.com", "동행인2", "ESCORT", phone(tag, 3))
        val escort2Cookie = login(escort2Username, "testPassword")
        createEscortProfile(escort2Cookie)
        verifyEscortDirectly(escort2Id)

        beginRecruitmentForTest(postId)
        val apply1Response = mvc.perform(
            post("/api/v1/applications/{postId}", postId).cookie(escort1Cookie)
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val application1Id = JsonPath.parse(apply1Response).read<Number>("$.data.id").toLong()

        val apply2Response = mvc.perform(
            post("/api/v1/applications/{postId}", postId).cookie(escort2Cookie)
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val application2Id = JsonPath.parse(apply2Response).read<Number>("$.data.id").toLong()

        // 의뢰인이 escort1의 지원 승인
        mvc.perform(patch("/api/v1/applications/{applicationId}/accept", application1Id).cookie(clientCookie))
            .andExpect(status().isOk())

        val accepted = applicationRepository.findById(application1Id).orElseThrow()
        val rejected = applicationRepository.findById(application2Id).orElseThrow()
        assertThat(accepted.status).isEqualTo(ApplicationStatus.ACCEPTED)
        assertThat(rejected.status).isEqualTo(ApplicationStatus.REJECTED)
        assertThat(postRepository.findById(postId).orElseThrow().postStatus).isEqualTo(PostStatus.MATCHED)

        // 3번째 동행인은 모집 중이지 않은 공고에 지원할 수 없다
        val escort3Username = "flow2-escort3-$tag"
        val escort3Id = signUp(escort3Username, "testPassword", "flow2-escort3-$tag@test.com", "동행인3", "ESCORT", phone(tag, 4))
        val escort3Cookie = login(escort3Username, "testPassword")
        createEscortProfile(escort3Cookie)
        verifyEscortDirectly(escort3Id)

        mvc.perform(post("/api/v1/applications/{postId}", postId).cookie(escort3Cookie))
            .andDo(print())
            .andExpect(status().isBadRequest())
    }

    // ── 시나리오 3 ─────────────────────────────────────────────
    // 다른 의뢰인의 지원 조회/승인과 다른 동행인의 단계 변경은 기존 컨트롤러 테스트가 담당한다.
    // 여기서는 공고 등록 자체의 인증·권한 경계만 검사한다.

    @Test
    @DisplayName("[통합] 비로그인 상태로 공고 등록 시도 시 401-113 반환")
    fun 비로그인_공고_등록_401() {
        val now = LocalDateTime.now()
        val resultActions: ResultActions = mvc.perform(
            post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    postJson(
                        now.plusDays(1), now.plusDays(1).plusHours(1),
                        now.plusDays(1).plusHours(2), now.plusDays(1).plusHours(4), 15000,
                    )
                )
        ).andDo(print())

        resultActions
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }

    @Test
    @DisplayName("[통합] 동행인 계정으로 공고 등록 시도 시 403 반환")
    fun 동행인_공고_등록_403() {
        val tag = tag()
        val escortUsername = "flow3-escort-$tag"
        signUp(escortUsername, "testPassword", "flow3-escort-$tag@test.com", "동행인", "ESCORT", phone(tag, 1))
        val escortCookie = login(escortUsername, "testPassword")

        val now = LocalDateTime.now()
        val resultActions: ResultActions = mvc.perform(
            post("/api/v1/posts")
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    postJson(
                        now.plusDays(1), now.plusDays(1).plusHours(1),
                        now.plusDays(1).plusHours(2), now.plusDays(1).plusHours(4), 15000,
                    )
                )
        ).andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    // ── 시나리오 4 ─────────────────────────────────────────────

    @Test
    @DisplayName("[통합] 결제를 취소한 공고는 승인부터 막혀 동행 완료·정산까지 가지 못한다")
    fun 결제_취소한_공고는_정산까지_가지_못한다() {
        val tag = tag()
        val hourlyPay = 15000

        val clientUsername = "flow4-client-$tag"
        signUp(clientUsername, "testPassword", "flow4-client-$tag@test.com", "의뢰인", "CLIENT", phone(tag, 1))
        val clientCookie = login(clientUsername, "testPassword")

        // 1. 공고 등록 (동행 예정 2시간)
        val now = LocalDateTime.now()
        val writeResponse = mvc.perform(
            post("/api/v1/posts")
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    postJson(
                        now.plusDays(1), now.plusDays(1).plusHours(1),
                        now.plusDays(1).plusHours(2), now.plusDays(1).plusHours(4), hourlyPay,
                    )
                )
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val writeCtx = JsonPath.parse(writeResponse)
        val postId = writeCtx.read<Number>("$.data.id").toLong()
        val paymentId = writeCtx.read<Number>("$.data.paymentId").toLong()

        // 2. 결제 승인
        val plannedAmount = (hourlyPay * 2).toString()
        confirmPayment(paymentId, clientCookie, tag, plannedAmount)

        // 3. 결제 취소 (공고 모집 중이므로 취소 허용)
        mvc.perform(
            delete("/api/v1/payments/{paymentId}", paymentId)
                .cookie(clientCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason": "병원 예약이 취소되었습니다."}""")
        )
            .andDo(print())
            .andExpect(status().isNoContent())
            .andExpect(jsonPath("$.statusCode").value("204-41"))

        verify(tossPaymentClient).callApiCancel(anyString(), anyString(), anyString())

        assertThat(paymentRepository.findSuccessPayByPostId(postId)).isEmpty()
        assertThat(paymentRepository.findByPostIdAndPaymentStatus(postId, PaymentStatus.READY)).hasSize(1)

        // 4. 미결제 공고에도 지원 자체는 가능
        val escortUsername = "flow4-escort-$tag"
        val escortUserId = signUp(escortUsername, "testPassword", "flow4-escort-$tag@test.com", "동행인", "ESCORT", phone(tag, 2))
        val escortCookie = login(escortUsername, "testPassword")
        createEscortProfile(escortCookie)
        verifyEscortDirectly(escortUserId)

        beginRecruitmentForTest(postId)
        val applyResponse = mvc.perform(
            post("/api/v1/applications/{postId}", postId).cookie(escortCookie)
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val applicationId = JsonPath.parse(applyResponse).read<Number>("$.data.id").toLong()

        // 5. 미결제 공고이므로 승인은 차단
        mvc.perform(patch("/api/v1/applications/{applicationId}/accept", applicationId).cookie(clientCookie))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-33"))
            .andExpect(jsonPath("$.msg").value("결제 완료 공고만 승인할 수 있습니다."))

        assertThat(postRepository.findById(postId).orElseThrow().postStatus).isEqualTo(PostStatus.OPEN)

        // 6. 매칭되지 않았으므로 동행 완료 처리도 차단
        mvc.perform(patch("/api/v1/posts/{postId}/escortComplete", postId).cookie(clientCookie))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-19"))

        // 7. 해당 동행인에 대한 정산은 생성되지 않음
        assertThat(settlementRepository.findAll())
            .noneMatch { it.escort.id == escortUserId }
    }
}
