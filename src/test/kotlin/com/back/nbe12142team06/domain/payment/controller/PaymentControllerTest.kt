package com.back.nbe12142team06.domain.payment.controller

import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.dto.PaymentCancelRequest
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc

    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var paymentService: PaymentService
    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var postService: PostService

    // 외부 결제 API 와 알림·정산은 실제로 호출하지 않는다.
    // (원래는 테스트마다 PaymentService 를 직접 new 했는데, 그러면 트랜잭션·캐시 프록시가 빠진 객체를 검증하게 된다)
    @field:MockitoBean private lateinit var tossPaymentClient: TossPaymentClient
    @field:MockitoBean private lateinit var settlementService: SettlementService
    @field:MockitoBean private lateinit var noticeService: NoticeService

    private var savedUser1Id: Long = 0
    private var savedUser2Id: Long = 0
    private var savedPayment1Id: Long = 0
    private var savedPayment2Id: Long = 0
    private var savedPost1Id: Long = 0
    private var savedPost2Id: Long = 0
    private lateinit var accessTokenCookie1: Cookie
    private lateinit var accessTokenCookie2: Cookie

    @BeforeEach
    fun init() {
        val username = "testUsername"
        val password = "testPassword"
        val email = "testEmail@test.test"
        val name = "김춘식"
        val role = "CLIENT"
        val gender = "MALE"
        val birthDate = "1990-05-20"
        val phoneNum = "010-1234-5678"
        val region = "서울시"

        val user1 = userService.signUp(
            UserSignUpRequest(
                username, password, email, name, Role.valueOf(role),
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                phoneNum, region
            )
        )
        savedUser1Id = user1.id

        val user2 = userService.signUp(
            UserSignUpRequest(
                username + "2", password + "2", email + "2", name + "2", Role.valueOf(role),
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                "010-9999-9999", region
            )
        )
        savedUser2Id = user2.id

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

        val post1 = postService.write(user1.id, postWriteRequest1)
        paymentRepository.testStatusDoneAndPutPaymentKey(post1.id, UUID.randomUUID().toString())

        val postWriteRequest2 = PostWriteRequest(
            title + "2", content + "2", postRegion + "2", hospitalName + "2", hospitalAddress + "2",
            hospitalLat, hospitalLng, pickupAddress + "2", pickupLat, pickupLng, hourlyPay,
            recruitStartAt, recruitEndAt, escortStartAt, escortEndAt,
            RideSelect.TAXI, RideSelect.TAXI, "", true
        )

        val post2 = postService.write(user1.id, postWriteRequest2)

        savedPayment1Id = post1.paymentId
        savedPayment2Id = post2.paymentId
        savedPost1Id = post1.id
        savedPost2Id = post2.id

        // user1로 로그인해 인증 쿠키 확보
        accessTokenCookie1 = login(username, password)
        accessTokenCookie2 = login(username + "2", password + "2")
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
    @DisplayName("[PaymentController] 결제 승인 - 성공")
    fun requestConfirm() {
        val paymentKey = "temp"
        val orderId = "temp"
        val amount = "60000"

        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", amount, paymentKey, orderId)))
            .whenever(tossPaymentClient).callApiConfirm(any(), any(), any())

        val payment = paymentService.confirm(
            PaymentConfirmRequest(paymentKey, orderId, amount), savedPayment2Id, savedUser1Id
        )

        assertThat(payment.paymentStatus).isEqualTo(PaymentStatus.DONE)
        // 시/분을 각각 비교하면 정각·정분을 넘기는 순간 실패하므로 오차 범위로 확인한다
        assertThat(payment.approvedAt).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
    }

    @Test
    @DisplayName("[PaymentController] 결제 승인 - 잘못 요청된 유저")
    fun requestConfirmFailUserId() {
        val paymentKey = "temp"
        val orderId = "temp"
        val amount = "10000"

        // 예외 발생 403번
        assertThatThrownBy {
            paymentService.confirm(
                PaymentConfirmRequest(paymentKey, orderId, amount), savedPayment1Id, savedUser2Id
            )
        }.isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    @DisplayName("[PaymentController] 결제 승인 - 잘못 요청된 공고")
    fun requestConfirmFailPostId() {
        val paymentKey = "temp"
        val orderId = "temp"
        val amount = "10000"
        val paymentId = 10000L

        // 예외 발생 404
        assertThatThrownBy {
            paymentService.confirm(PaymentConfirmRequest(paymentKey, orderId, amount), paymentId, savedUser1Id)
        }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    @DisplayName("[PaymentController] 결제 승인 - 토스 결제 승인 실패")
    fun requestConfirmFailConfirm() {
        val paymentKey = "temp"
        val orderId = "temp"
        // 결제 대기(READY) 상태이고 금액이 맞는 payment2 로 요청해야 토스 호출까지 도달한다.
        // 결제 완료 상태인 payment1 + 금액 10000 으로 요청하면 승인 전 검증에서 걸려 다른 이유로 예외가 났다
        val amount = "60000"

        doThrow(InvalidException(11, "결제 승인에 실패했습니다."))
            .whenever(tossPaymentClient).callApiConfirm(any(), any(), any())

        // 예외 발생 400번
        assertThatThrownBy {
            paymentService.confirm(
                PaymentConfirmRequest(paymentKey, orderId, amount), savedPayment2Id, savedUser1Id
            )
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 승인에 실패했습니다.")

        verify(tossPaymentClient, times(1)).callApiConfirm(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentController] 결제 데이터 가져오기")
    fun paymentList() {
        val resultActions = mvc.perform(
            get("/api/v1/payments")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("paymentList"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-43"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 불러왔습니다."))
            .andExpect(jsonPath("$.data[0]").exists())
            .andExpect(jsonPath("$.data[1]").exists())
    }

    @Test
    @DisplayName("[PaymentController] 결제 상세 데이터 가져오기 - 성공")
    fun getPayment() {
        val resultActions = mvc.perform(
            get("/api/v1/payments/$savedPayment2Id")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("getPayment"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-44"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 불러왔습니다."))
            .andExpect(jsonPath("$.data.id").value(savedPayment2Id))
            .andExpect(jsonPath("$.data.amount").value(60_000))
            .andExpect(jsonPath("$.data.hourlyPaySnapshot").value(15_000))
            .andExpect(jsonPath("$.data.hours").value(4.0))
            .andExpect(jsonPath("$.data.orderId").isEmpty())
            .andExpect(jsonPath("$.data.paymentStatus").value("READY"))
    }

    @Test
    @DisplayName("[PaymentController] 결제 상세 데이터 가져오기 - 잘못된 결제 ID")
    fun getPaymentFailInvalidPaymentId() {
        val paymentId = 10000L

        val resultActions = mvc.perform(
            get("/api/v1/payments/$paymentId")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("getPayment"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-41"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[PaymentController] 결제 상세 데이터 가져오기 - 다른 회원이 조회")
    fun getPaymentFailOtherMember() {
        val resultActions = mvc.perform(
            get("/api/v1/payments/$savedPayment1Id")
                .cookie(accessTokenCookie2)
                .param("userId", savedUser2Id.toString())
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("getPayment"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-41"))
            .andExpect(jsonPath("$.msg").value("사용자의 결제 정보가 아닙니다."))
    }

    @Test
    @DisplayName("[PaymentController] 결제 취소 - 성공")
    fun requestCancel() {
        val cancelReason = "결제 취소 사유"

        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", "60000", "paymentKey", "orderId")))
            .whenever(tossPaymentClient).callApiCancel(any(), any(), any())

        paymentService.cancel(savedUser1Id, savedPayment1Id, PaymentCancelRequest(cancelReason))

        // 취소 선점(@Modifying clearAutomatically) 이 영속성 컨텍스트를 비우므로
        // 반환된 엔티티가 아니라 DB 에 반영된 결제를 다시 읽어 확인한다
        val canceledPayment = paymentRepository.findByIdOrNull(savedPayment1Id)!!

        assertThat(canceledPayment.paymentStatus).isEqualTo(PaymentStatus.CANCELED)
        assertThat(canceledPayment.cancelReason).isEqualTo("결제 취소 사유")
        assertThat(canceledPayment.canceledAt).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
        assertThat(canceledPayment.balanceAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    @DisplayName("[PaymentController] 결제 취소 - 토스 결제 취소 실패")
    fun requestCancelFailCancel() {
        val cancelReason = "결제 취소 사유"

        doThrow(InvalidException(12, "결제 취소에 실패했습니다."))
            .whenever(tossPaymentClient).callApiCancel(any(), any(), any())

        // 예외 발생 400번
        assertThatThrownBy {
            paymentService.cancel(savedUser1Id, savedPayment1Id, PaymentCancelRequest(cancelReason))
        }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("결제 취소에 실패했습니다.")
    }

    private fun confirmRequestBody(paymentKey: String, orderId: String, amount: String): String =
        """
        {
            "paymentKey": "$paymentKey",
            "orderId": "$orderId",
            "amount": "$amount"
        }
        """.trimIndent()

    @Test
    @DisplayName("[PaymentController] POST 결제 승인 - 성공 응답 규약")
    fun requestConfirmApi() {
        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", "60000", "pk_test", "order_test")))
            .whenever(tossPaymentClient).callApiConfirm(any(), any(), any())

        val resultActions = mvc.perform(
            post("/api/v1/payments/$savedPayment2Id/confirm")
                .cookie(accessTokenCookie1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmRequestBody("pk_test", "order_test", "60000"))
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("requestConfirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-41"))
            .andExpect(jsonPath("$.msg").value("결제 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.paymentKey").value("pk_test"))
            .andExpect(jsonPath("$.data.orderId").value("order_test"))
            .andExpect(jsonPath("$.data.amount").value("60000"))

        assertThat(paymentRepository.findByIdOrNull(savedPayment2Id)!!.paymentStatus)
            .isEqualTo(PaymentStatus.DONE)
    }

    @Test
    @DisplayName("[PaymentController] POST 결제 승인 - 다른 회원의 결제면 403-41")
    fun requestConfirmApiFailOtherMember() {
        val resultActions = mvc.perform(
            post("/api/v1/payments/$savedPayment1Id/confirm")
                .cookie(accessTokenCookie2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmRequestBody("pk_test", "order_test", "60000"))
        ).andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-41"))
            .andExpect(jsonPath("$.msg").value("사용자의 결제 정보가 아닙니다."))

        verify(tossPaymentClient, never()).callApiConfirm(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentController] POST 결제 승인 - 없는 결제면 404-41")
    fun requestConfirmApiFailInvalidPaymentId() {
        val resultActions = mvc.perform(
            post("/api/v1/payments/10000/confirm")
                .cookie(accessTokenCookie1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmRequestBody("pk_test", "order_test", "60000"))
        ).andDo(print())

        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-41"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[PaymentController] POST 결제 승인 - 요청 금액이 다르면 400-42")
    fun requestConfirmApiFailAmountMismatch() {
        val resultActions = mvc.perform(
            post("/api/v1/payments/$savedPayment2Id/confirm")
                .cookie(accessTokenCookie1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmRequestBody("pk_test", "order_test", "10000"))
        ).andDo(print())

        resultActions
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-42"))
            .andExpect(jsonPath("$.msg").value("결제 금액 정보가 유효하지 않습니다."))

        // 금액 검증에서 막혔으므로 외부 승인 API 는 호출되지 않는다
        verify(tossPaymentClient, never()).callApiConfirm(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentController] DELETE 결제 취소 - 성공 응답 규약")
    fun requestCancelApi() {
        doReturn(ResponseEntity.ok(TossConfirmResponse("계좌이체", "60000", "paymentKey", "orderId")))
            .whenever(tossPaymentClient).callApiCancel(any(), any(), any())

        val resultActions = mvc.perform(
            delete("/api/v1/payments/$savedPayment1Id")
                .cookie(accessTokenCookie1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason": "병원 예약이 취소되었습니다."}""")
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("cancelPayment"))
            .andExpect(status().isNoContent())
            .andExpect(jsonPath("$.statusCode").value("204-41"))
            .andExpect(jsonPath("$.msg").value("결제 취소 성공했습니다."))

        assertThat(paymentRepository.findByIdOrNull(savedPayment1Id)!!.paymentStatus)
            .isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    @DisplayName("[PaymentController] DELETE 결제 취소 - 다른 회원의 결제면 403-41")
    fun requestCancelApiFailOtherMember() {
        val resultActions = mvc.perform(
            delete("/api/v1/payments/$savedPayment1Id")
                .cookie(accessTokenCookie2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason": "남의 결제 취소"}""")
        ).andDo(print())

        resultActions
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-41"))

        verify(tossPaymentClient, never()).callApiCancel(any(), any(), any())
    }

    @Test
    @DisplayName("[PaymentController] 공고별 결제 조회 - 미결제 결제를 돌려준다")
    fun getPaymentByPost() {
        val resultActions = mvc.perform(
            get("/api/v1/payments/posts/$savedPost2Id")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(PaymentController::class.java))
            .andExpect(handler().methodName("getPaymentByPost"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-45"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 불러왔습니다."))
            .andExpect(jsonPath("$.data.id").value(savedPayment2Id))
            .andExpect(jsonPath("$.data.postId").value(savedPost2Id))
            .andExpect(jsonPath("$.data.paymentStatus").value("READY"))
    }

    @Test
    @DisplayName("[PaymentController] 공고별 결제 조회 - 미결제 결제가 없으면 404-41")
    fun getPaymentByPostFailAlreadyPaid() {
        // post1 의 결제는 이미 DONE 이라 미결제 건이 없다
        val resultActions = mvc.perform(
            get("/api/v1/payments/posts/$savedPost1Id")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-41"))
            .andExpect(jsonPath("$.msg").value("결제 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[PaymentController] 로그인 없이 결제 목록 조회 - 401-113")
    fun paymentListWithoutLogin() {
        mvc.perform(get("/api/v1/payments"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
    }

    @Test
    @DisplayName("[PaymentController] 동행인이 결제 목록 조회 - 403-1")
    fun paymentListWithEscortRole() {
        userService.signUp(
            UserSignUpRequest(
                "escortUsername", "testPassword", "escort@test.test", "박동행", Role.ESCORT,
                Gender.MALE, LocalDate.of(1995, 5, 20), "010-7777-7777", "서울시"
            )
        )
        val escortCookie = login("escortUsername", "testPassword")

        mvc.perform(
            get("/api/v1/payments")
                .cookie(escortCookie)
        )
            .andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
    }
}
