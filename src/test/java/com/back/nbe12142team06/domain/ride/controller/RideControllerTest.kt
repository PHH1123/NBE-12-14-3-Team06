package com.back.nbe12142team06.domain.ride.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.service.UserService
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
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
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class RideControllerTest {

    @field:Autowired private lateinit var mvc: MockMvc

    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var postService: PostService
    @field:Autowired private lateinit var rideService: RideService
    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository

    private var savedPost1Id: Long = 0
    private var savedRide1Id: Long = 0

    // 1: 공고를 쓴 의뢰인, 2: 그 공고에 승인된 동행 매니저
    private lateinit var accessTokenCookie1: Cookie
    private lateinit var accessTokenCookie2: Cookie

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

        val user1 = userService.signUp(
            UserSignUpRequest(
                username, password, email, name, Role.CLIENT,
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                phoneNum, region
            )
        )

        val user2 = userService.signUp(
            UserSignUpRequest(
                username + "2", password + "2", email + "2", name + "2", Role.ESCORT,
                Gender.valueOf(gender),
                LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE),
                phoneNum + "2", region
            )
        )

        // 공고를 쓰면 이동 정보(병원 이동·귀가 이동)가 함께 만들어진다
        val post1 = postService.write(user1.id!!, postWriteRequest())
        paymentRepository.testStatusDoneAndPutPaymentKey(post1.id!!, UUID.randomUUID().toString())

        val application = Application.builder()
            .post(postRepository.findById(post1.id!!).get())
            .escort(user2)
            .build()
        application.accept()
        applicationRepository.save(application)

        savedPost1Id = post1.id!!
        savedRide1Id = rideService.findByPostId(savedPost1Id).first().requiredId

        // user로 로그인해 인증 쿠키 확보
        accessTokenCookie1 = login(username, password)
        accessTokenCookie2 = login(username + "2", password + "2")
    }

    private fun postWriteRequest() = PostWriteRequest(
        "정형외과 동행 구합니다", "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.", "서울",
        "서울성모병원", "서울 서초구 반포대로 222",
        BigDecimal.valueOf(37.5012743), BigDecimal.valueOf(127.0051893),
        "서울 서초구 잠원동 10-1", BigDecimal.valueOf(37.5160000), BigDecimal.valueOf(127.0200000),
        15_000,
        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(6),
        LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(7).plusHours(4),
        RideSelect.TAXI, RideSelect.TAXI, "", true
    )

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
    @DisplayName("[RideController] 해당 공고 이동 목록 - 동행 매니저 조회 성공")
    fun listByPostEscortSuccess() {
        val resultActions = mvc.perform(
            get("/api/v1/rides/posts/$savedPost1Id")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(RideController::class.java))
            .andExpect(handler().methodName("getListByPostId"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-81"))
            .andExpect(jsonPath("$.msg").value("해당 공고의 이동 정보를 불러왔습니다."))
            .andExpect(jsonPath("$.data[0].direction").value("TO_HOSPITAL"))
            .andExpect(jsonPath("$.data[0].selected").value("TAXI"))
    }

    @Test
    @DisplayName("[RideController] 이동 상세 정보 - 성공")
    fun rideDetails() {
        val resultActions = mvc.perform(
            get("/api/v1/rides/$savedRide1Id")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(RideController::class.java))
            .andExpect(handler().methodName("getRide"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-82"))
            .andExpect(jsonPath("$.msg").value("이동 정보를 불러왔습니다."))
            .andExpect(jsonPath("$.data.direction").value("TO_HOSPITAL"))
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
            // 공고 등록 때 고른 이동수단이 그대로 들어간다
            .andExpect(jsonPath("$.data.selected").value("TAXI"))
    }

    @Test
    @DisplayName("[RideController] 이동 상세 정보 - 이동 정보 없음")
    fun rideDetailsFailNotFound() {
        val resultActions = mvc.perform(
            get("/api/v1/rides/10000")
                .cookie(accessTokenCookie1)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(RideController::class.java))
            .andExpect(handler().methodName("getRide"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-82"))
            .andExpect(jsonPath("$.msg").value("이동 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[RideController] 이동 상세 정보 - 권한 부족")
    fun rideDetailsFailForbidden() {
        // 상세 조회는 의뢰인만 볼 수 있어서, 승인된 동행 매니저도 막힌다
        val resultActions = mvc.perform(
            get("/api/v1/rides/$savedRide1Id")
                .cookie(accessTokenCookie2)
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(RideController::class.java))
            .andExpect(handler().methodName("getRide"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-81"))
            .andExpect(jsonPath("$.msg").value("이동 정보를 조회할 권한이 없습니다."))
    }
}
