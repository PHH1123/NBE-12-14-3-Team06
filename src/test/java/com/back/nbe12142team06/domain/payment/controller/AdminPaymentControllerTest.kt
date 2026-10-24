package com.back.nbe12142team06.domain.payment.controller

import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
 * 관리자 결제 API 테스트.
 * 다른 테스트가 남긴 결제(승인 시각이 현재)와 섞이지 않도록, 집계·목록 조회는
 * 과거 구간(30~10일 전)에 데이터를 만들고 그 구간만 조회한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminPaymentControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc
    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var adminCookie: Cookie
    private lateinit var clientCookie: Cookie

    // 조회 구간: 20일 전 ~ 10일 전
    private val rangeStart: LocalDate = LocalDate.now().minusDays(20)
    private val rangeEnd: LocalDate = LocalDate.now().minusDays(10)

    private var donePaymentId: Long = 0        // 15일 전 결제 완료 150,000원
    private var partialPaymentId: Long = 0     // 14일 전 부분 취소, 잔액 37,500원
    private var recentPaymentId: Long = 0      // 5일 전 결제 완료 (기본 조회 구간 안)
    private var canceledPaymentId: Long = 0    // 30일 전 취소 (조회 구간 밖)

    @BeforeEach
    fun init() {
        userRepository.save(createUser("adminPay", Role.ADMIN, "010-5555-0001"))
        val client = userRepository.save(createUser("clientPay", Role.CLIENT, "010-5555-0002"))
        val post = postRepository.save(createPost(client))

        donePaymentId = paymentRepository.save(
            createPayment(
                post, PaymentStatus.DONE, LocalDateTime.now().minusDays(15),
                BigDecimal.valueOf(150_000), BigDecimal.valueOf(150_000)
            )
        ).requiredId
        partialPaymentId = paymentRepository.save(
            createPayment(
                post, PaymentStatus.PARTIAL_CANCELED, LocalDateTime.now().minusDays(14),
                BigDecimal.valueOf(75_000), BigDecimal.valueOf(37_500)
            )
        ).requiredId
        recentPaymentId = paymentRepository.save(
            createPayment(
                post, PaymentStatus.DONE, LocalDateTime.now().minusDays(5),
                BigDecimal.valueOf(30_000), BigDecimal.valueOf(30_000)
            )
        ).requiredId
        canceledPaymentId = paymentRepository.save(
            createPayment(
                post, PaymentStatus.CANCELED, LocalDateTime.now().minusDays(30),
                BigDecimal.valueOf(45_000), BigDecimal.ZERO
            )
        ).requiredId

        adminCookie = login("adminPay")
        clientCookie = login("clientPay")
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User {
        return User(
            username, passwordEncoder.encode("testPassword"), "$username@test.test", "김춘식",
            role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
        )
    }

    private fun createPost(client: User): Post {
        return Post(
            client = client,
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
            escortEndAt = LocalDateTime.now().minusDays(20).plusHours(10)
        )
    }

    private fun createPayment(
        post: Post,
        status: PaymentStatus,
        approvedAt: LocalDateTime,
        amount: BigDecimal,
        balanceAmount: BigDecimal
    ): Payment {
        return Payment(
            id = null,
            amount = amount,
            hourlyPaySnapshot = 15_000,
            hours = BigDecimal.TEN,
            post = post,
            method = "CARD",
            paymentStatus = status,
            orderId = "order-${status.name}-${approvedAt.toLocalDate()}",
            paymentKey = "pk-${status.name}-${approvedAt.toLocalDate()}",
            approvedAt = approvedAt,
            balanceAmount = balanceAmount
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
    @DisplayName("[AdminPaymentController] 일별 결제 통계 조회")
    fun getDailyAnalytics() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/payments/analytics/daily")
                .cookie(adminCookie)
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        // 일별 통계는 결제 완료·부분 취소 건의 결제 금액을 날짜별로 더한다
        resultActions
            .andExpect(handler().handlerType(AdminPaymentController::class.java))
            .andExpect(handler().methodName("getDailyAnalytics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-151"))
            .andExpect(jsonPath("$.msg").value("관리자 일별 결제 통계 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].approvedAt").value(LocalDate.now().minusDays(15).param()))
            .andExpect(jsonPath("$.data[0].paidAmount").value(150_000))
            .andExpect(jsonPath("$.data[0].counts").value(1))
            .andExpect(jsonPath("$.data[1].approvedAt").value(LocalDate.now().minusDays(14).param()))
            .andExpect(jsonPath("$.data[1].paidAmount").value(75_000))
    }

    @Test
    @DisplayName("[AdminPaymentController] 기간별 결제·환불 금액 통계 조회")
    fun getAnalyticsByPeriod() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/payments/analytics")
                .cookie(adminCookie)
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        // 150,000(전액 유지) + 37,500(잔액) = 187,500 / 환불은 75,000 - 37,500 = 37,500
        resultActions
            .andExpect(handler().methodName("getAnalyticsByPeriod"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-152"))
            .andExpect(jsonPath("$.msg").value("관리자 기간별 결제 및 취소 환불 금액 통계 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.paidAmount").value(187_500))
            .andExpect(jsonPath("$.data.canceledAmount").value(37_500))
            .andExpect(jsonPath("$.data.counts").value(2))
    }

    @Test
    @DisplayName("[AdminPaymentController] 기간별 결제 목록 조회 - id 내림차순")
    fun getPaymentList() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/payments")
                .cookie(adminCookie)
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("getPaymentList"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-153"))
            .andExpect(jsonPath("$.msg").value("관리자 기간별 결제 목록 조회가 완료되었습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].id").value(partialPaymentId))
            .andExpect(jsonPath("$.data.content[0].amount").value(75_000))
            .andExpect(jsonPath("$.data.content[0].balanceAmount").value(37_500))
            .andExpect(jsonPath("$.data.content[0].name").value("김춘식"))
            .andExpect(jsonPath("$.data.content[1].id").value(donePaymentId))
    }

    @Test
    @DisplayName("[AdminPaymentController] 기간별 결제 목록 조회 - 페이지 크기 적용")
    fun getPaymentListWithPaging() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/payments")
                .cookie(adminCookie)
                .param("page", "0")
                .param("size", "1")
                .param("startDate", rangeStart.param())
                .param("endDate", rangeEnd.param())
        ).andDo(print())

        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].id").value(partialPaymentId))
            .andExpect(jsonPath("$.data.totalElements").value(2))
    }

    @Test
    @DisplayName("[AdminPaymentController] 조회 기간을 생략하면 최근 7일만 조회한다")
    fun getPaymentListWithDefaultPeriod() {
        val resultActions = mvc.perform(
            get("/api/v1/admin/payments")
                .cookie(adminCookie)
        ).andDo(print())

        // 5일 전 결제는 들어오고, 15일 전 결제는 빠진다
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.id == $recentPaymentId)]").exists())
            .andExpect(jsonPath("$.data.content[?(@.id == $donePaymentId)]").isEmpty())
    }

    @Test
    @DisplayName("[AdminPaymentController] 결제 상태 변경")
    fun updateStatus() {
        val resultActions = mvc.perform(
            patch("/api/v1/admin/payments/$recentPaymentId/status")
                .cookie(adminCookie)
                .param("status", PaymentStatus.CANCELED.name)
        ).andDo(print())

        resultActions
            .andExpect(handler().methodName("updateStatus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-154"))
            .andExpect(jsonPath("$.msg").value("관리자 결제 상태가 변경되었습니다."))

        em.flush()
        em.clear()

        assertThat(paymentRepository.findById(recentPaymentId).get().paymentStatus)
            .isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    @DisplayName("[AdminPaymentController] 허용되지 않은 상태로 변경하면 400-72")
    fun updateStatusWithInvalidTransition() {
        val resultActions = mvc.perform(
            patch("/api/v1/admin/payments/$canceledPaymentId/status")
                .cookie(adminCookie)
                .param("status", PaymentStatus.DONE.name)
        ).andDo(print())

        resultActions
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-72"))
            .andExpect(jsonPath("$.msg").value("'결제 취소' 상태의 결제는 '결제 완료' 상태로 바꿀 수 없습니다."))
    }

    @Test
    @DisplayName("[AdminPaymentController] 일반 회원이 관리자 API 를 호출하면 403-1")
    fun forbiddenForNonAdmin() {
        mvc.perform(
            get("/api/v1/admin/payments")
                .cookie(clientCookie)
        )
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }

    @Test
    @DisplayName("[AdminPaymentController] 로그인 없이 관리자 API 를 호출하면 401-113")
    fun unauthorizedWithoutLogin() {
        mvc.perform(get("/api/v1/admin/payments"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }
}
