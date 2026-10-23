package com.back.nbe12142team06.domain.application.controller

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.transaction.annotation.Transactional

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

import org.junit.jupiter.api.Assertions.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mvcPost
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ApplicationControllerTest {

    @field:Autowired

    private lateinit var mvc: MockMvc

    @field:Autowired

    private lateinit var userRepository: UserRepository

    @field:Autowired

    private lateinit var postRepository: PostRepository

    @field:Autowired

    private lateinit var passwordEncoder: PasswordEncoder

    @field:Autowired

    private lateinit var applicationRepository: ApplicationRepository

    @field:Autowired

    private lateinit var applicationService: ApplicationService

    @field:Autowired

    private lateinit var paymentRepository: PaymentRepository

    @field:Autowired

    private lateinit var escortProfileRepository: EscortProfileRepository

    @field:Autowired

    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    // 테스트에서 토스는 반드시 목으로 타야해서 추가했습니다.
    @field:MockitoBean
    private lateinit var tossPaymentClient: TossPaymentClient

    private var testPostId = 0L
    private lateinit var clientAccessTokenCookie: Cookie
    private lateinit var escortAccessTokenCookie: Cookie

    @BeforeEach
    fun setUp() {

        // 공고를 작성할 의뢰인
        var client = User(
                "client1",
                passwordEncoder.encode("testPassword"),
                "client1@test.com",
                "의뢰인1",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-1111-1111",
                "수원"
        )

        userRepository.save(client)

        // 공고에 지원할 동행인
        var escort = User(
                "escort1",
                passwordEncoder.encode("testPassword"),
                "escort1@test.com",
                "동행인1",
                Role.ESCORT,
                Gender.FEMALE,
                LocalDate.of(1995, 1, 1),
                "010-2222-2222",
                "수원"
        )

        userRepository.save(escort)

        // 테스트용 동행인 프로필 생성 (교육 이수 완료 상태)
        var escortProfile = EscortProfile(escort, "ㅎㅎ", "은행은행", escort.name, "1234")
        escortProfileRepository.save(escortProfile)
        this.escortProfileRepository.verify(escortProfile.userId, LocalDateTime.now())

        // 테스트용 공고
        var post = Post.builder()
                .client(client)
                .title("테스트 공고")
                .content("병원 동행 테스트")
                .region("수원")
                .hospitalName("아주대학교병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시 팔달구")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))
                .escortStartAt(LocalDateTime.now().plusDays(3))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(3))
                .patientNote("테스트 환자")
                .reportRequired(false)
                .build()

        testPostId = postRepository.save(post).id!!

        // 테스트 과정에 결제 데이터가 필요해서 추가
        var payment = Payment.builder()
                .amount(post.totalPay)
                .hourlyPaySnapshot(post.hourlyPay)
                .hours(post.escortHours)
                .post(post)
                .paymentStatus(PaymentStatus.DONE)
                .build()

        paymentRepository.save(payment)

        // 의뢰인 로그인
        clientAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "client1",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 동행인 로그인
        escortAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "escort1",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 정상 지원")
    fun t1() {

        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value("201-21"))
                .andExpect(jsonPath("$.msg").value("지원이 완료되었습니다."))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.postId").value(testPostId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
    }


    @Test
    @DisplayName("[ApplicationController] 지원하기 - 일시 정지 회원은 지원할 수 없다")
    fun t1_1() {

        // given: 정상 회원을 일시 정지 상태로 변경
        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        escort.suspend(1L, "테스트용 일시 정지")
        userRepository.saveAndFlush(escort)

        // when: 일시 정지된 회원이 지원 시도
        var resultActions = mvc.perform(
                mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        // then: 지원 거부
        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-31"))
                .andExpect(jsonPath("$.msg")
                        .value("활성 상태의 회원만 지원할 수 있습니다."))

        // 실제 지원 내역도 생성되지 않아야 함
        assertEquals(
                0,
                applicationRepository.findAllByPostIdWithEscort(testPostId).size
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 모집 시작 전에는 지원 불가")
    fun applyBeforeRecruitStart() {

        // given: 모집 시작일을 내일로 변경
        var post = postRepository.findById(testPostId)
                .orElseThrow()

        ReflectionTestUtils.setField(
                post,
                "recruitStartAt",
                LocalDateTime.now().plusDays(1)
        )

        postRepository.saveAndFlush(post)

        // when: 동행매니저가 지원 요청
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))

                // then: 모집 기간 오류 반환
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-26"))
                .andExpect(jsonPath("$.msg")
                        .value("모집 기간 내에만 지원할 수 있습니다."))

        // 실제 지원 데이터가 저장되지 않았는지 확인
        assertTrue(
                applicationRepository
                        .findAllByPostIdWithEscort(testPostId)
                        .isEmpty()
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 모집 마감 후에는 OPEN이어도 지원 불가")
    fun applyAfterRecruitEnd() {

        // given: 모집 마감일을 1분 전으로 변경
        var post = postRepository.findById(testPostId)
                .orElseThrow()

        ReflectionTestUtils.setField(
                post,
                "recruitEndAt",
                LocalDateTime.now().minusMinutes(1)
        )

        postRepository.saveAndFlush(post)

        // when: 동행매니저가 지원 요청
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))

                // then: 모집 기간 오류 반환
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-26"))
                .andExpect(jsonPath("$.msg")
                        .value("모집 기간 내에만 지원할 수 있습니다."))

        // 지원 내역이 생성되지 않았는지 확인
        assertTrue(
                applicationRepository
                        .findAllByPostIdWithEscort(testPostId)
                        .isEmpty()
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 영구 정지 회원은 지원할 수 없다")
    fun t1_2() {

        // given: 로그인한 동행매니저를 영구 정지
        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        escort.ban(1L, "테스트용 영구 정지")
        userRepository.saveAndFlush(escort)

        // when: 기존 로그인 쿠키로 지원 시도
        var resultActions = mvc.perform(
                mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        // then: 지원 거부
        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-31"))
                .andExpect(jsonPath("$.msg")
                        .value("활성 상태의 회원만 지원할 수 있습니다."))

        // 지원 데이터가 생성되지 않았는지 확인
        assertEquals(
                0,
                applicationRepository.findAllByPostIdWithEscort(testPostId).size
        )
    }

    @Test
    @DisplayName("[ApplicationService] 진행 중인 동행이 있으면 탈퇴 제한 대상")
    fun t1_3() {

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        // 승인된 지원 생성
        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        applicationRepository.save(application)

        // 공고를 매칭 완료 → 동행 진행 중 상태로 변경
        post.match()
        post.startProgress(LocalDateTime.now())

        // 진행 중 동행이 있으므로 true
        var result = applicationService.hasInProgressEscort(escort.id)

        assertTrue(result)
    }

    @Test
    @DisplayName("[ApplicationService] 매칭만 완료된 경우 탈퇴 제한 대상이 아님")
    fun t1_4() {

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        applicationRepository.save(application)

        // 매칭만 완료된 상태
        post.match()

        // 아직 동행이 시작되지 않았으므로 false
        var result = applicationService.hasInProgressEscort(escort.id)

        assertFalse(result)
    }

    @Test
    @DisplayName("[ApplicationService] 대기 중인 지원만 있으면 진행 중 동행이 아니다")
    fun t1_5() {

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        // PENDING 지원 생성 (기본 상태)
        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        applicationRepository.save(application)

        // PENDING + OPEN 상태에서는 false
        var result = applicationService.hasInProgressEscort(escort.id)

        assertFalse(result)
    }

    @Test
    @DisplayName("[ApplicationService] 다른 동행매니저의 진행 중 동행은 조회되지 않는다")
    fun t1_6() {

        var escort1 = userRepository.findByUsername("escort1")
                .orElseThrow()

        // 두 번째 동행매니저 생성
        var escort2 = User(
                "escort2",
                passwordEncoder.encode("testPassword"),
                "escort2@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(escort2)

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        // escort2의 동행을 진행 중 상태로 생성
        var application = Application.builder()
                .post(post)
                .escort(escort2)
                .build()

        application.accept()
        applicationRepository.save(application)

        post.match()
        post.startProgress(LocalDateTime.now())

        // escort2는 진행 중인 동행이 존재
        assertTrue(applicationService.hasInProgressEscort(escort2.id))

        // escort1은 진행 중인 동행이 없음
        assertFalse(applicationService.hasInProgressEscort(escort1.id))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 일시 정지된 동행매니저는 승인할 수 없다")
    fun t1_7() {

        // given: 정상 회원일 때 공고에 지원
        mvc.perform(
                mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie)
        ).andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 지원 이후 동행매니저 일시 정지
        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        escort.suspend(1L, "테스트용 일시 정지")
        userRepository.saveAndFlush(escort)

        // when: 의뢰인이 정지된 동행매니저의 지원을 승인 시도
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept", application.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-32"))
                .andExpect(jsonPath("$.msg")
                        .value("활성 상태의 동행매니저만 승인할 수 있습니다."))

        // then: 지원과 공고 상태는 변경되지 않아야 함
        assertEquals(ApplicationStatus.PENDING,
                applicationRepository.findById(application.id!!)
                        .orElseThrow().status)

        assertEquals(PostStatus.OPEN,
                postRepository.findById(testPostId)
                        .orElseThrow().postStatus)
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 영구 정지된 동행매니저는 승인할 수 없다")
    fun t1_8() {

        // given: 정상 회원일 때 공고에 지원
        mvc.perform(
                mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie)
        ).andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 지원 이후 동행매니저 영구 정지
        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        escort.ban(1L, "테스트용 영구 정지")
        userRepository.saveAndFlush(escort)

        // when: 의뢰인이 영구 정지된 회원의 지원을 승인 시도
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept", application.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-32"))
                .andExpect(jsonPath("$.msg")
                        .value("활성 상태의 동행매니저만 승인할 수 있습니다."))

        // then: 지원은 PENDING, 공고는 OPEN 유지
        assertEquals(ApplicationStatus.PENDING,
                applicationRepository.findById(application.id!!)
                        .orElseThrow().status)

        assertEquals(PostStatus.OPEN,
                postRepository.findById(testPostId)
                        .orElseThrow().postStatus)
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 중복 지원 시 409 반환")
    fun t2() {

        // 첫 번째 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        // 같은 사용자가 같은 공고에 두 번째 지원
        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value("409-21"))
                .andExpect(jsonPath("$.msg").value("이미 지원한 공고입니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - CLIENT가 지원 시 403 반환 (SecurityConfig 에서 ESCORT 만 허용)")
    fun t3() {

        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(clientAccessTokenCookie))
                .andDo(print())

        // 컨트롤러에 도달하기 전에 URL 권한 규칙에서 막히므로 handler 검증은 없다.
        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 모집 중이 아닌 공고 지원 시 400 반환")
    fun t4() {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        // 이미 매칭 완료된 공고 생성
        var matchedPost = Post.builder()
                .client(client)
                .title("매칭 완료된 공고")
                .content("이미 매칭된 공고입니다.")
                .region("수원")
                .hospitalName("아주대학교병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시 팔달구")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))
                .escortStartAt(LocalDateTime.now().plusDays(3))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(3))
                .patientNote("테스트 환자")
                .reportRequired(false)
                .postStatus(PostStatus.MATCHED)
                .build()

        var matchedPostId = postRepository.save(matchedPost).id

        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", matchedPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-21"))
                .andExpect(jsonPath("$.msg")
                        .value("모집 중인 공고에만 지원할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 존재하지 않는 공고 지원 시 404 반환")
    fun t5() {

        var notExistingPostId = 999L

        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", notExistingPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-11"))
                .andExpect(jsonPath("$.msg")
                        .value("%d번 공고가 없습니다.".format(notExistingPostId)))
    }

    @Test
    @DisplayName("[ApplicationController] 공고별 지원 목록 조회 - 정상 조회")
    fun t6() {

        // 먼저 해당 공고에 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        // 지원 목록 조회
        var resultActions = mvc
                .perform(get("/api/v1/applications/posts/{postId}", testPostId)
                        .cookie(clientAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-21"))
                .andExpect(jsonPath("$.msg").value("지원 목록 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].applicationId").exists())
                .andExpect(jsonPath("$.data.content[0].escortId").exists())
                .andExpect(jsonPath("$.data.content[0].escortName").value("동행인1"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
    }

    @Test
    @DisplayName("[ApplicationController] 공고별 지원 목록 조회 - 지원자가 없을 때 빈 배열 반환")
    fun t7() {

        var resultActions = mvc
                .perform(get("/api/v1/applications/posts/{postId}", testPostId)
                        .cookie(clientAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-21"))
                .andExpect(jsonPath("$.msg").value("지원 목록 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
    }

    @Test
    @DisplayName("[ApplicationController] 공고별 지원 목록 조회 - 페이징 정상 동작")
    fun t7_1() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort1 = userRepository.findByUsername("escort1")
                .orElseThrow()

        var escort2 = User(
                "escort2",
                passwordEncoder.encode("testPassword"),
                "escort2@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1995, 2, 2),
                "010-2222-3333",
                "서울"
        )

        var escort3 = User(
                "escort3",
                passwordEncoder.encode("testPassword"),
                "escort3@test.com",
                "동행인3",
                Role.ESCORT,
                Gender.FEMALE,
                LocalDate.of(1995, 3, 3),
                "010-2222-4444",
                "인천"
        )

        userRepository.save(escort2)
        userRepository.save(escort3)

        applicationRepository.save(
                Application.builder()
                        .post(post)
                        .escort(escort1)
                        .build()
        )

        applicationRepository.save(
                Application.builder()
                        .post(post)
                        .escort(escort2)
                        .build()
        )

        applicationRepository.save(
                Application.builder()
                        .post(post)
                        .escort(escort3)
                        .build()
        )

        var resultActions = mvc.perform(
                get("/api/v1/applications/posts/{postId}", testPostId)
                        .param("page", "0")
                        .param("size", "2")
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].escortName").value("동행인3"))
                .andExpect(jsonPath("$.data.content[1].escortName").value("동행인2"))
    }

    @Test
    @DisplayName("[ApplicationController] 공고별 지원 목록 조회 - 존재하지 않는 공고 조회 시 404 반환")
    fun t8() {

        var notExistingPostId = 999L

        var resultActions = mvc
                .perform(get("/api/v1/applications/posts/{postId}", notExistingPostId)
                        .cookie(clientAccessTokenCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("list"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-11"))
                .andExpect(jsonPath("$.msg").value("%d번 공고가 없습니다.".format(notExistingPostId)))
    }

    @Test
    @DisplayName("[ApplicationController] 공고별 지원 목록 조회 - 다른 의뢰인의 공고 조회 시 403 반환")
    fun t9() {
        // 다른 의뢰인 생성
        var otherClient = User(
                "client2",
                passwordEncoder.encode("testPassword"),
                "client2@test.com",
                "의뢰인2",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(otherClient)

        var otherClientAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "username": "client2",
                                      "password": "testPassword"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        var resultActions = mvc.perform(
                get("/api/v1/applications/posts/{postId}", testPostId)
                        .cookie(otherClientAccessTokenCookie)
        )

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-22"))
                .andExpect(jsonPath("$.msg")
                        .value("본인 공고의 지원 목록만 조회할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 정상 승인")
    fun t10() {

        // 동행인이 먼저 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        // 방금 생성된 지원 조회
        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 의뢰인이 지원 승인
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-22"))
                .andExpect(jsonPath("$.msg").value("지원 승인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.applicationId").value(application.id))
                .andExpect(jsonPath("$.data.postId").value(testPostId))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))

        // 승인 후 실제 DB 상태 확인
        var acceptedApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        var matchedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.ACCEPTED,
                acceptedApplication.status
        )

        assertEquals(
                testPostId,
                acceptedApplication.acceptedPostId
        )

        assertEquals(
                PostStatus.MATCHED,
                matchedPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 다른 의뢰인이 승인 시 403 반환")
    fun t11() {

        // 동행인이 먼저 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 다른 의뢰인 생성
        var otherClient = User(
                "client2",
                passwordEncoder.encode("testPassword"),
                "client2@test.com",
                "의뢰인2",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(otherClient)

        var otherClientAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                            {
                              "username": "client2",
                              "password": "testPassword"
                            }
                            """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", application.id)
                        .cookie(otherClientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-23"))
                .andExpect(jsonPath("$.msg")
                        .value("본인 공고의 지원만 승인할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 이미 승인된 지원 재승인 시 400 반환")
    fun t12() {

        // 동행인이 먼저 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 첫 번째 승인
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept", application.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andDo(print())
                .andExpect(status().isOk())

        // 같은 지원을 다시 승인
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-24"))
                .andExpect(jsonPath("$.msg")
                        .value("대기 중인 지원만 승인할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 한 명 승인 시 나머지 지원자는 자동 거절")
    fun t13() {

        // 두 번째 동행인 생성
        var escort2 = User(
                "escort2",
                passwordEncoder.encode("testPassword"),
                "escort2@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                "010-4444-4444",
                "인천"
        )

        userRepository.save(escort2)

        // 동행 매니저 프로필 (프로필이 있고 교육을 이수해야 지원 가능)
        var escort2Profile = escortProfileRepository.save(EscortProfile(
                escort2,
                "동행인2입니다.",
                "오픈은행",
                "동행인2",
                "123-0000000-222"
        ))
        this.escortProfileRepository.verify(escort2Profile.userId, LocalDateTime.now())

        var escort2AccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "username": "escort2",
                                      "password": "testPassword"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 첫 번째 동행인 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        // 두 번째 동행인 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escort2AccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        // 두 지원 조회
        var applications = applicationRepository.findAllByPostIdWithEscort(testPostId)

        var firstApplication = applications.stream()
                .filter { application ->
                        application.escort!!.username.equals("escort1") }
                .findFirst()
                .orElseThrow()

        var secondApplication = applications.stream()
                .filter { application ->
                        application.escort!!.username.equals("escort2") }
                .findFirst()
                .orElseThrow()

        // 첫 번째 동행인 승인
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept",
                                firstApplication.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andDo(print())
                .andExpect(status().isOk())

        // 승인된 지원 상태 확인
        var acceptedApplication = applicationRepository
                .findById(firstApplication.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.ACCEPTED,
                acceptedApplication.status
        )

        // 나머지 지원자는 자동 거절됐는지 확인
        var rejectedApplication = applicationRepository
                .findById(secondApplication.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.REJECTED,
                rejectedApplication.status
        )

        // 공고도 매칭 완료됐는지 확인
        var matchedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        assertEquals(
                PostStatus.MATCHED,
                matchedPost.postStatus
        )

        // 이미 자동 거절된 두 번째 지원을 다시 승인 시도
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept",
                        secondApplication.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value("409-34"))
                .andExpect(jsonPath("$.msg")
                        .value("이미 다른 동행매니저가 승인되었거나 동행 일정이 중복되어 승인할 수 없습니다."))

    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 존재하지 않는 지원 승인 시 404 반환")
    fun t14() {

        var notExistingApplicationId = 999L

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", notExistingApplicationId)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 같은 동행인의 시간 겹치는 다른 지원 자동 거절")
    fun t15() {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        // 기존 testPost와 시간이 겹치는 두 번째 공고 생성
        var overlappingPost = Post.builder()
                .client(client)
                .title("시간 겹치는 테스트 공고")
                .content("동행 시간이 겹치는 공고입니다.")
                .region("수원")
                .hospitalName("성빈센트병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2770))
                .hospitalLng(BigDecimal.valueOf(127.0276))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2700))
                .pickupLng(BigDecimal.valueOf(127.0300))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))

                // 기존 testPost: 3일 뒤 ~ 3일 뒤 + 3시간
                // 새 공고: 3일 뒤 + 1시간 ~ 3일 뒤 + 4시간
                // -> 서로 시간이 겹침
                .escortStartAt(LocalDateTime.now().plusDays(3).plusHours(1))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(4))

                .patientNote("테스트 환자")
                .reportRequired(false)
                .build()

        var overlappingPostId = postRepository.save(overlappingPost).id

        // 같은 동행인이 첫 번째 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        // 같은 동행인이 시간이 겹치는 두 번째 공고에도 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", overlappingPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        // 첫 번째 공고 지원 조회
        var firstApplication = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 두 번째 공고 지원 조회
        var overlappingApplication = applicationRepository
                .findAllByPostIdWithEscort(overlappingPostId)
                .get(0)

        // 첫 번째 공고에서 동행인 선정
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept",
                                firstApplication.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andDo(print())
                .andExpect(status().isOk())

        // DB 상태 다시 조회
        var acceptedApplication = applicationRepository
                .findById(firstApplication.id!!)
                .orElseThrow()

        var rejectedApplication = applicationRepository
                .findById(overlappingApplication.id!!)
                .orElseThrow()

        // 선택된 지원은 ACCEPTED
        assertEquals(
                ApplicationStatus.ACCEPTED,
                acceptedApplication.status
        )

        // 같은 동행인의 시간이 겹치는 다른 지원은 자동 REJECTED
        assertEquals(
                ApplicationStatus.REJECTED,
                rejectedApplication.status
        )

        // 선정된 공고는 MATCHED
        var matchedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        assertEquals(
                PostStatus.MATCHED,
                matchedPost.postStatus
        )

        // 다른 공고 자체는 계속 모집 가능
        var otherPost = postRepository
                .findById(overlappingPostId!!)
                .orElseThrow()

        assertEquals(
                PostStatus.OPEN,
                otherPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 같은 동행인의 시간이 겹치지 않는 다른 지원은 대기 유지")
    fun t16() {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        // 기존 testPost와 시간이 겹치지 않는 두 번째 공고 생성
        var nonOverlappingPost = Post.builder()
                .client(client)
                .title("시간 안 겹치는 테스트 공고")
                .content("동행 시간이 겹치지 않는 공고입니다.")
                .region("수원")
                .hospitalName("성빈센트병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2770))
                .hospitalLng(BigDecimal.valueOf(127.0276))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2700))
                .pickupLng(BigDecimal.valueOf(127.0300))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))

                // 기존 testPost: 3일 뒤 ~ 3일 뒤 + 3시간
                // 새 공고:3일 뒤 + 4시간 ~ 3일 뒤 + 6시간
                // -> 시간이 겹치지 않음
                .escortStartAt(LocalDateTime.now().plusDays(3).plusHours(4))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(6))

                .patientNote("테스트 환자")
                .reportRequired(false)
                .build()

        var nonOverlappingPostId = postRepository.save(nonOverlappingPost).id

        // 같은 동행인이 첫 번째 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        // 같은 동행인이 시간이 안 겹치는 두 번째 공고에도 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", nonOverlappingPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        // 첫 번째 공고 지원 조회
        var firstApplication = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 두 번째 공고 지원 조회
        var secondApplication = applicationRepository
                .findAllByPostIdWithEscort(nonOverlappingPostId)
                .get(0)

        // 첫 번째 공고에서 동행인 선정
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept",
                                firstApplication.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andDo(print())
                .andExpect(status().isOk())

        // DB 상태 다시 조회
        var acceptedApplication = applicationRepository
                .findById(firstApplication.id!!)
                .orElseThrow()

        var pendingApplication = applicationRepository
                .findById(secondApplication.id!!)
                .orElseThrow()

        // 첫 번째 지원은 ACCEPTED
        assertEquals(
                ApplicationStatus.ACCEPTED,
                acceptedApplication.status
        )

        // 시간이 겹치지 않는 다른 지원은 PENDING 유지
        assertEquals(
                ApplicationStatus.PENDING,
                pendingApplication.status
        )

        // 첫 번째 공고는 MATCHED
        var matchedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        assertEquals(
                PostStatus.MATCHED,
                matchedPost.postStatus
        )

        // 두 번째 공고는 계속 OPEN
        var otherPost = postRepository
                .findById(nonOverlappingPostId!!)
                .orElseThrow()

        assertEquals(
                PostStatus.OPEN,
                otherPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 이미 승인된 다른 공고와 시간이 겹치면 승인 불가")
    fun t17() {

        // 첫 번째 공고에 동행인이 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var firstApplication = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 첫 번째 공고에서 동행인 승인
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept",
                                firstApplication.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andExpect(status().isOk())

        // 첫 번째 지원이 실제 ACCEPTED인지 확인
        var acceptedApplication = applicationRepository
                .findById(firstApplication.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.ACCEPTED,
                acceptedApplication.status
        )

        // 두 번째 의뢰인 생성
        var client2 = User(
                "client2",
                passwordEncoder.encode("testPassword"),
                "client2@test.com",
                "의뢰인2",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(client2)

        // 두 번째 의뢰인 로그인
        var client2AccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "username": "client2",
                                      "password": "testPassword"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 첫 번째 공고와 시간이 겹치는 두 번째 공고 생성
        var overlappingPost = Post.builder()
                .client(client2)
                .title("시간 겹치는 두 번째 공고")
                .content("이미 승인된 일정과 겹치는 공고입니다.")
                .region("수원")
                .hospitalName("성빈센트병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2770))
                .hospitalLng(BigDecimal.valueOf(127.0276))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2700))
                .pickupLng(BigDecimal.valueOf(127.0300))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))

                // 기존 testPost: 3일 뒤 ~ 3일 뒤 + 3시간
                // 새 공고: 3일 뒤 + 1시간 ~ 3일 뒤 + 4시간
                // -> 시간 겹침
                .escortStartAt(LocalDateTime.now().plusDays(3).plusHours(1))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(4))

                .patientNote("테스트 환자")
                .reportRequired(false)
                .build()

        var overlappingPostId = postRepository.save(overlappingPost).id

        var payment = Payment.builder()
                .amount(overlappingPost.totalPay)
                .hourlyPaySnapshot(overlappingPost.hourlyPay)
                .hours(overlappingPost.escortHours)
                .post(overlappingPost)
                .paymentStatus(PaymentStatus.DONE)
                .build()
        paymentRepository.save(payment)

        // 이미 다른 공고에서 ACCEPTED된 동행인이
        // 시간이 겹치는 새 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", overlappingPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var overlappingApplication = applicationRepository
                .findAllByPostIdWithEscort(overlappingPostId)
                .get(0)

        // 두 번째 의뢰인이 해당 동행인을 선정하려고 시도
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept",
                        overlappingApplication.id)
                        .cookie(client2AccessTokenCookie)
        ).andDo(print())

        // 이미 승인된 일정과 시간이 겹치므로 승인 실패
        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value("409-35"))
                .andExpect(jsonPath("$.msg")
                        .value("이미 매칭된 다른 공고와 동행 시간이 겹칩니다."))


        // 승인 실패했으므로 두 번째 지원은 PENDING 유지
        var pendingApplication = applicationRepository
                .findById(overlappingApplication.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.PENDING,
                pendingApplication.status
        )

        // 두 번째 공고 역시 OPEN 유지
        var stillOpenPost = postRepository
                .findById(overlappingPostId!!)
                .orElseThrow()

        assertEquals(
                PostStatus.OPEN,
                stillOpenPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 거절 - 정상 거절")
    fun t18() {

        // 동행인이 먼저 지원
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 의뢰인이 지원 거절
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/reject", application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-23"))
                .andExpect(jsonPath("$.msg").value("지원 거절이 완료되었습니다."))

        var rejectedApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.REJECTED,
                rejectedApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 거절 - 다른 의뢰인이 거절 시 403 반환")
    fun t19() {

        // 동행인이 먼저 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 다른 의뢰인 생성
        var otherClient = User(
                "client2",
                passwordEncoder.encode("testPassword"),
                "client2@test.com",
                "의뢰인2",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(otherClient)

        // 다른 의뢰인 로그인
        var otherClientAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                    {
                                      "username": "client2",
                                      "password": "testPassword"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 다른 의뢰인이 지원 거절 시도
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/reject",
                        application.id)
                        .cookie(otherClientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("reject"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-24"))
                .andExpect(jsonPath("$.msg")
                        .value("본인 공고의 지원만 거절할 수 있습니다."))

        // 실패했으므로 상태는 여전히 PENDING
        var pendingApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.PENDING,
                pendingApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 거절 - 이미 거절된 지원 재거절 시 400 반환")
    fun t20() {

        // 동행인이 먼저 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 첫 번째 거절
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/reject",
                                application.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andExpect(status().isOk())

        // 같은 지원을 다시 거절
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/reject",
                        application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("reject"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-27"))
                .andExpect(jsonPath("$.msg")
                        .value("대기 중인 지원만 거절할 수 있습니다."))

        // 상태는 그대로 REJECTED
        var rejectedApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.REJECTED,
                rejectedApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 거절 - 존재하지 않는 지원 거절 시 404 반환")
    fun t21() {

        var notExistingApplicationId = 999L

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/reject",
                        notExistingApplicationId)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("reject"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 정상 취소")
    fun t22() {

        // 동행인이 먼저 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 동행인이 본인의 지원 취소
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        application.id)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원 취소가 완료되었습니다."))

        // 실제 DB 상태 확인
        var canceledApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.CANCELED,
                canceledApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 승인 후 모집 마감 전 취소")
    fun t23() {

        // 동행인이 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 의뢰인이 지원 승인
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/accept",
                                application.id)
                                .cookie(clientAccessTokenCookie)
                )
                .andExpect(status().isOk())

        // 취소 전 노쇼 횟수 확인
        var escortProfile = escortProfileRepository
                .findById(application.escort!!.id)
                .orElseThrow()

        var beforeNoShowCount = escortProfile.noShowCount

        // 승인된 동행인이 취소
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        application.id)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원 취소가 완료되었습니다."))

        // 취소 후 지원 상태 확인
        var canceledApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        // 취소 후 공고 상태 확인
        var reopenedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        // 취소 후 동행인 프로필 확인
        var updatedEscortProfile = escortProfileRepository
                .findById(application.escort!!.id)
                .orElseThrow()

        // 승인된 지원은 NO_SHOW 처리
        assertEquals(
                ApplicationStatus.NO_SHOW,
                canceledApplication.status
        )

        // 재매칭을 위해 acceptedPostId 초기화
        assertNull(canceledApplication.acceptedPostId)

        // 동행인 노쇼 횟수 1 증가
        assertEquals(
                beforeNoShowCount + 1,
                updatedEscortProfile.noShowCount
        )

        // 모집 마감 전이므로 공고 다시 OPEN
        assertEquals(
                PostStatus.OPEN,
                reopenedPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 승인 후 모집 마감 후 취소")
    fun t24() {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        // 모집 마감 시간이 이미 지난 매칭 공고 생성
        var expiredMatchedPost = Post.builder()
                .client(client)
                .title("모집 마감 지난 공고")
                .content("모집 마감 후 동행인 취소 테스트")
                .region("수원")
                .hospitalName("아주대학교병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시 팔달구")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)

                // 모집 마감 시간이 이미 지남
                .recruitStartAt(LocalDateTime.now().minusDays(2))
                .recruitEndAt(LocalDateTime.now().minusMinutes(1))

                // 실제 동행 시간은 아직 미래
                .escortStartAt(LocalDateTime.now().plusDays(1))
                .escortEndAt(LocalDateTime.now().plusDays(1).plusHours(3))

                .patientNote("테스트 환자")
                .reportRequired(false)
                .postStatus(PostStatus.MATCHED)
                .build()

        postRepository.save(expiredMatchedPost)

        // 이미 승인된 지원 생성
        var application = Application.builder()
                .post(expiredMatchedPost)
                .escort(escort)
                .build()

        application.accept()
        applicationRepository.save(application)

        // 취소 전 노쇼 횟수
        var escortProfile = escortProfileRepository
                .findById(escort.id)
                .orElseThrow()

        var beforeNoShowCount = escortProfile.noShowCount

        // 승인된 동행인이 취소
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        application.id)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원 취소가 완료되었습니다."))

        // 취소 후 지원 확인
        var canceledApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        // 취소 후 공고 확인
        var canceledPost = postRepository
                .findById(expiredMatchedPost.id!!)
                .orElseThrow()

        // 취소 후 동행인 프로필 확인
        var updatedEscortProfile = escortProfileRepository
                .findById(escort.id)
                .orElseThrow()

        // 승인된 지원은 NO_SHOW
        assertEquals(
                ApplicationStatus.NO_SHOW,
                canceledApplication.status
        )

        // 재매칭할 수 없으므로 기존 승인 공고 ID 초기화
        assertNull(canceledApplication.acceptedPostId)

        // 노쇼 횟수 +1
        assertEquals(
                beforeNoShowCount + 1,
                updatedEscortProfile.noShowCount
        )

        // 모집 마감 후이므로 공고 취소
        assertEquals(
                PostStatus.CANCELED,
                canceledPost.postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 다른 동행인이 취소 시 403 반환")
    fun t25() {

        // 기존 동행인(escort1)이 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 다른 동행인 생성
        var otherEscort = User(
                "escort2",
                passwordEncoder.encode("testPassword"),
                "escort2@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(otherEscort)

        // 다른 동행인 로그인
        var otherEscortAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "escort2",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 다른 동행인이 escort1의 지원을 취소 시도
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        application.id)
                        .cookie(otherEscortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-25"))
                .andExpect(jsonPath("$.msg")
                        .value("본인이 지원한 내역만 취소할 수 있습니다."))

        // 취소되지 않고 기존 PENDING 상태 유지
        var unchangedApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.PENDING,
                unchangedApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 이미 취소된 지원 재취소 시 400 반환")
    fun t26() {

        // 동행인이 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 첫 번째 취소
        mvc.perform(
                        patch("/api/v1/applications/{applicationId}/cancel",
                                application.id)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isOk())

        // 같은 지원을 다시 취소
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        application.id)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-28"))
                .andExpect(jsonPath("$.msg")
                        .value("취소할 수 없는 지원 상태입니다."))

        // 상태는 그대로 CANCELED
        var canceledApplication = applicationRepository
                .findById(application.id!!)
                .orElseThrow()

        assertEquals(
                ApplicationStatus.CANCELED,
                canceledApplication.status
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 존재하지 않는 지원 취소 시 404 반환")
    fun t27() {

        var notExistingApplicationId = 999L

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/cancel",
                        notExistingApplicationId)
                        .cookie(escortAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 동행 진행 상태 변경 - 전체 진행 순서 정상 기록")
    fun t28() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        post.match()

        applicationRepository.save(application)

        var progressList = listOf(
                EscortProgress.DEPARTED,
                EscortProgress.TO_HOSPITAL,
                EscortProgress.AT_HOSPITAL,
                EscortProgress.TO_HOME,
                EscortProgress.ARRIVED_HOME
        )

        for (progress in progressList) {

            mvc.perform(
                            patch("/api/v1/applications/{applicationId}/progress",
                                    application.id)
                                    .cookie(escortAccessTokenCookie)
                                    .contentType("application/json")
                                    .content("""
                                        {
                                          "progress": "%s"
                                        }
                                        """.format(progress.name))
                    )
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value("200-25"))
        }

        // 모든 진행 단계가 실제로 저장됐는지 확인
        for (progress in progressList) {
            var savedProgress = escortProgressLogRepository
                    .findByApplicationAndProgress(application, progress)
                    .orElseThrow()
                    .progress

            assertEquals(progress, savedProgress)
        }

        // 마지막 진행 상태가 ARRIVED_HOME인지 확인
        var latestProgress = escortProgressLogRepository
                .findTopByApplicationOrderByOccurredAtDescIdDesc(application)
                .orElseThrow()
                .progress

        assertEquals(
                EscortProgress.ARRIVED_HOME,
                latestProgress
        )

        var progressedPost = postRepository
                .findById(testPostId)
                .orElseThrow()

        assertEquals(
                PostStatus.IN_PROGRESS,
                progressedPost.postStatus
        )

        var departedAt = escortProgressLogRepository
                .findByApplicationAndProgress(
                        application,
                        EscortProgress.DEPARTED
                )
                .orElseThrow()
                .occurredAt

        assertEquals(
                departedAt,
                progressedPost.escortStartAt
        )
    }

    @Test
    @DisplayName("[ApplicationController] 동행 진행 상태 변경 - 순서 건너뛰기 시 400 반환")
    fun t29() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        post.match()

        applicationRepository.save(application)

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/progress",
                        application.id)
                        .cookie(escortAccessTokenCookie)
                        .contentType("application/json")
                        .content("""
                            {
                              "progress": "AT_HOSPITAL"
                            }
                            """)
        ).andDo(print())

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-31"))
                .andExpect(jsonPath("$.msg")
                        .value("동행 진행 상태를 순서대로 변경해야 합니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 동행 진행 상태 변경 - 다른 동행인이 변경 시 403 반환")
    fun t30() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort1 = userRepository.findByUsername("escort1")
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort1)
                .build()

        application.accept()
        post.match()

        applicationRepository.save(application)

        // 다른 동행인 생성
        var escort2 = User(
                "escort2",
                passwordEncoder.encode("testPassword"),
                "escort2@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(escort2)

        var escort2AccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "escort2",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/progress",
                        application.id)
                        .cookie(escort2AccessTokenCookie)
                        .contentType("application/json")
                        .content("""
                        {
                          "progress": "DEPARTED"
                        }
                        """)
        ).andDo(print())

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-26"))
                .andExpect(jsonPath("$.msg")
                        .value("본인의 동행 진행 상태만 변경할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 동행 진행 상태 변경 - PENDING 상태에서 변경 시 400 반환")
    fun t31() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        applicationRepository.save(application)

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/progress",
                        application.id)
                        .cookie(escortAccessTokenCookie)
                        .contentType("application/json")
                        .content("""
                            {
                              "progress": "DEPARTED"
                            }
                            """)
        ).andDo(print())

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-29"))
                .andExpect(jsonPath("$.msg")
                        .value("승인된 지원만 동행 진행 상태를 변경할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 동행 진행 상태 변경 - 동행 완료 후 변경 시 400 반환")
    fun t32() {

        var post = postRepository.findById(testPostId)
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        post.match()

        applicationRepository.save(application)

        var progressList = listOf(
                EscortProgress.DEPARTED,
                EscortProgress.TO_HOSPITAL,
                EscortProgress.AT_HOSPITAL,
                EscortProgress.TO_HOME,
                EscortProgress.ARRIVED_HOME
        )

        // ARRIVED_HOME까지 정상 진행
        for (progress in progressList) {
            mvc.perform(
                            patch("/api/v1/applications/{applicationId}/progress",
                                    application.id)
                                    .cookie(escortAccessTokenCookie)
                                    .contentType("application/json")
                                    .content("""
                                        {
                                          "progress": "%s"
                                        }
                                        """.format(progress.name))
                    )
                    .andExpect(status().isOk())
        }

        // 이미 귀가 완료된 뒤 다시 상태 변경 시도
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/progress",
                        application.id)
                        .cookie(escortAccessTokenCookie)
                        .contentType("application/json")
                        .content("""
                            {
                              "progress": "DEPARTED"
                            }
                            """)
        ).andDo(print())

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-30"))
                .andExpect(jsonPath("$.msg")
                        .value("이미 동행이 완료되었습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원자 프로필 조회 - 정상 조회")
    fun t33() {

        // 동행인이 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andDo(print())
                .andExpect(status().isCreated())

        // 생성된 지원 조회
        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 의뢰인이 지원자의 프로필 조회
        var resultActions = mvc.perform(
                get("/api/v1/applications/{applicationId}/escort-profile",
                        application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("escortProfile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-27"))
                .andExpect(jsonPath("$.msg")
                        .value("지원자 프로필 조회가 완료되었습니다."))
                .andExpect(jsonPath("$.data.escortId")
                        .value(application.escort!!.id))
                .andExpect(jsonPath("$.data.name")
                        .value("동행인1"))
                .andExpect(jsonPath("$.data.verified").exists())
                .andExpect(jsonPath("$.data.completedCount").exists())
                .andExpect(jsonPath("$.data.rating").exists())
                .andExpect(jsonPath("$.data.ratingCount").exists())
                .andExpect(jsonPath("$.data.noShowCount").exists())
    }

    @Test
    @DisplayName("[ApplicationController] 지원자 프로필 조회 - 다른 의뢰인이 조회 시 403 반환")
    fun t34() {

        // 동행인이 공고에 지원
        mvc.perform(
                        mvcPost("/api/v1/applications/{postId}", testPostId)
                                .cookie(escortAccessTokenCookie)
                )
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        // 다른 의뢰인 생성
        var otherClient = User(
                "client2",
                passwordEncoder.encode("testPassword"),
                "client2@test.com",
                "의뢰인2",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "010-3333-3333",
                "서울"
        )

        userRepository.save(otherClient)

        // 다른 의뢰인 로그인
        var otherClientAccessTokenCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "client2",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        // 다른 의뢰인이 지원자 프로필 조회 시도
        var resultActions = mvc.perform(
                get("/api/v1/applications/{applicationId}/escort-profile",
                        application.id)
                        .cookie(otherClientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-20"))
                .andExpect(jsonPath("$.msg")
                        .value("본인 공고의 지원자 프로필만 조회할 수 있습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원자 프로필 조회 - 존재하지 않는 지원 조회 시 404 반환")
    fun t35() {

        var notExistingApplicationId = 999L

        var resultActions = mvc.perform(
                get("/api/v1/applications/{applicationId}/escort-profile",
                        notExistingApplicationId)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("escortProfile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("404-24"))
                .andExpect(jsonPath("$.msg")
                        .value("지원을 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("[ApplicationController] 지원하기 - 교육 미이수 동행인 지원 시 403 반환")
    fun t36() {

        // 교육을 이수하지 않은 동행인 생성
        var unverifiedEscort = User(
                "escort9",
                passwordEncoder.encode("testPassword"),
                "escort9@test.com",
                "미이수동행인",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1995, 1, 1),
                "010-9999-0009",
                "수원"
        )
        userRepository.save(unverifiedEscort)
        escortProfileRepository.save(EscortProfile(
                unverifiedEscort, "미이수", "오픈은행", "미이수동행인", "999-9999"
        ))

        var unverifiedCookie = mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                {
                                  "username": "escort9",
                                  "password": "testPassword"
                                }
                                """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!

        var resultActions = mvc
                .perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(unverifiedCookie))
                .andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-21"))
                .andExpect(jsonPath("$.msg").value("교육 영상 시청을 완료한 후 지원할 수 있습니다."))
    }

    /**
     * 공고를 하나 더 만든다. 결제는 호출부에서 원하는 상태로 붙인다.
     * (setUp 의 공고는 결제가 DONE 이라 "결제되지 않은 공고" 케이스를 만들 수 없다)
     */
    private fun savePost(title: String): Post {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        var post = Post.builder()
                .client(client)
                .title(title)
                .content("결제 상태별 승인 테스트")
                .region("수원")
                .hospitalName("아주대학교병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시 팔달구")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(1))
                .recruitEndAt(LocalDateTime.now().plusDays(2))
                .escortStartAt(LocalDateTime.now().plusDays(3))
                .escortEndAt(LocalDateTime.now().plusDays(3).plusHours(3))
                .patientNote("테스트 환자")
                .reportRequired(false)
                .build()

        return postRepository.save(post)
    }

    private fun savePayment(post: Post, paymentStatus: PaymentStatus): Payment {

        var payment = Payment.builder()
                .amount(post.totalPay)
                .hourlyPaySnapshot(post.hourlyPay)
                .hours(post.escortHours)
                .post(post)
                .paymentStatus(paymentStatus)
                .build()

        return paymentRepository.save(payment)
    }

    /** 교육까지 이수한 동행인을 추가로 만들고 로그인 쿠키를 돌려준다. */
    private fun saveEscort(username: String): Cookie {

        var escort = User(
                username,
                passwordEncoder.encode("testPassword"),
                username + "@test.com",
                username,
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                "010-5555-" + username.replace(Regex("\\D"), "0").substring(0, 4),
                "수원"
        )

        userRepository.save(escort)

        var escortProfile = escortProfileRepository.save(EscortProfile(
                escort,
                username + " 입니다.",
                "오픈은행",
                username,
                "123-0000000-333"
        ))

        this.escortProfileRepository.verify(escortProfile.userId, LocalDateTime.now())

        return mvc.perform(
                        mvcPost("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                        {
                                          "username": "%s",
                                          "password": "testPassword"
                                        }
                                        """.format(username))
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken")!!
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 결제 전(READY) 공고는 지원은 되지만 승인 시 400 반환")
    fun t37() {

        // 공고 등록 직후와 같은 상태 — 아직 내지 않은 결제 1건
        var unpaidPost = savePost("결제 전 공고")
        savePayment(unpaidPost, PaymentStatus.READY)

        // 지원은 허용된다. apply 는 결제 상태를 보지 않는다
        // (미결제 공고는 공개 목록 GET /api/v1/posts 에서 빠지지만, 공고 번호를 알면 지원할 수 있다)
        mvc.perform(mvcPost("/api/v1/applications/{postId}", unpaidPost.id)
                        .cookie(escortAccessTokenCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(unpaidPost.id)
                .get(0)

        // 승인은 막힌다
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-33"))
                .andExpect(jsonPath("$.msg").value("결제 완료 공고만 승인할 수 있습니다."))

        // 승인 직전 상태가 그대로 남아 있어야 한다 (지원은 대기, 공고는 모집 중)
        assertEquals(
                ApplicationStatus.PENDING,
                applicationRepository.findById(application.id!!).orElseThrow().status
        )

        assertEquals(
                PostStatus.OPEN,
                postRepository.findById(unpaidPost.id!!).orElseThrow().postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 승인 - 결제가 취소된 공고는 승인 시 400 반환")
    fun t38() {

        // 결제를 취소하면 백엔드가 원래 결제를 CANCELED 로 바꾸고
        // 같은 금액의 READY 결제를 새로 만들어 둔다(Payment.cancelPayment). 그 상태를 그대로 만든다.
        var canceledPaymentPost = savePost("결제 취소된 공고")
        savePayment(canceledPaymentPost, PaymentStatus.CANCELED)
        savePayment(canceledPaymentPost, PaymentStatus.READY)

        // 승인 가드가 보는 "성공한 결제"가 없는 상태
        assertEquals(
                0,
                paymentRepository.findSuccessPayByPostId(canceledPaymentPost.id).size
        )

        mvc.perform(mvcPost("/api/v1/applications/{postId}", canceledPaymentPost.id)
                        .cookie(escortAccessTokenCookie))
                .andExpect(status().isCreated())

        var application = applicationRepository
                .findAllByPostIdWithEscort(canceledPaymentPost.id)
                .get(0)

        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", application.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-33"))
                .andExpect(jsonPath("$.msg").value("결제 완료 공고만 승인할 수 있습니다."))

        // 매칭이 되지 않으므로 동행 진행 → 동행 완료 → 정산까지 이어질 수 없다
        assertEquals(
                PostStatus.OPEN,
                postRepository.findById(canceledPaymentPost.id!!).orElseThrow().postStatus
        )
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 노쇼 후 다른 동행인이 같은 공고에 재매칭된다")
    fun t39() {

        // 첫 번째 동행인 지원 → 승인
        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(escortAccessTokenCookie))
                .andExpect(status().isCreated())

        var firstApplication = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .get(0)

        mvc.perform(patch("/api/v1/applications/{applicationId}/accept", firstApplication.id)
                        .cookie(clientAccessTokenCookie))
                .andExpect(status().isOk())

        // 승인된 동행인이 취소 → 노쇼 처리되고 모집 마감 전이라 공고는 다시 모집 중
        mvc.perform(patch("/api/v1/applications/{applicationId}/cancel", firstApplication.id)
                        .cookie(escortAccessTokenCookie))
                .andExpect(status().isOk())

        assertEquals(
                PostStatus.OPEN,
                postRepository.findById(testPostId).orElseThrow().postStatus
        )

        // 두 번째 동행인이 같은 공고에 지원
        var secondEscortCookie = saveEscort("escort7")

        mvc.perform(mvcPost("/api/v1/applications/{postId}", testPostId)
                        .cookie(secondEscortCookie))
                .andDo(print())
                .andExpect(status().isCreated())

        var secondApplication = applicationRepository
                .findAllByPostIdWithEscort(testPostId)
                .stream()
                .filter { application -> application.escort!!.username.equals("escort7") }
                .findFirst()
                .orElseThrow()

        // 재매칭 — 승인까지 성공해야 한다
        // (acceptedPostId 가 unique 라서, 노쇼 처리가 그 값을 비우지 않으면 여기서 제약 위반으로 막힌다)
        var resultActions = mvc.perform(
                patch("/api/v1/applications/{applicationId}/accept", secondApplication.id)
                        .cookie(clientAccessTokenCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("200-22"))
                .andExpect(jsonPath("$.msg").value("지원 승인이 완료되었습니다."))

        var rematched = applicationRepository
                .findById(secondApplication.id!!)
                .orElseThrow()

        assertEquals(ApplicationStatus.ACCEPTED, rematched.status)
        assertEquals(testPostId, rematched.acceptedPostId)

        // 공고는 다시 매칭 완료
        assertEquals(
                PostStatus.MATCHED,
                postRepository.findById(testPostId).orElseThrow().postStatus
        )

        // 노쇼 처리된 첫 번째 지원은 그대로 남는다
        var noShowApplication = applicationRepository
                .findById(firstApplication.id!!)
                .orElseThrow()

        assertEquals(ApplicationStatus.NO_SHOW, noShowApplication.status)
        assertNull(noShowApplication.acceptedPostId)
    }

    @Test
    @DisplayName("[ApplicationController] 지원 취소 - 모집 마감 후 노쇼면 공고가 취소되어 재매칭 불가")
    fun t40() {

        var client = userRepository.findByUsername("client1")
                .orElseThrow()

        var escort = userRepository.findByUsername("escort1")
                .orElseThrow()

        // 모집 마감 시간이 이미 지난 매칭 공고
        var expiredMatchedPost = Post.builder()
                .client(client)
                .title("모집 마감 지난 매칭 공고")
                .content("모집 마감 후 노쇼 재매칭 테스트")
                .region("수원")
                .hospitalName("아주대학교병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시 팔달구")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now().minusDays(2))
                .recruitEndAt(LocalDateTime.now().minusMinutes(1))
                .escortStartAt(LocalDateTime.now().plusDays(1))
                .escortEndAt(LocalDateTime.now().plusDays(1).plusHours(3))
                .patientNote("테스트 환자")
                .reportRequired(false)
                .postStatus(PostStatus.MATCHED)
                .build()

        postRepository.save(expiredMatchedPost)
        savePayment(expiredMatchedPost, PaymentStatus.DONE)

        var application = Application.builder()
                .post(expiredMatchedPost)
                .escort(escort)
                .build()

        application.accept()
        applicationRepository.save(application)

        // 승인된 동행인이 취소 → 모집 마감 후라 공고 자체가 취소된다
        mvc.perform(patch("/api/v1/applications/{applicationId}/cancel", application.id)
                        .cookie(escortAccessTokenCookie))
                .andExpect(status().isOk())

        assertEquals(
                PostStatus.CANCELED,
                postRepository.findById(expiredMatchedPost.id!!).orElseThrow().postStatus
        )

        // 다른 동행인은 지원부터 막힌다 — 재매칭되지 않는다
        var secondEscortCookie = saveEscort("escort8")

        var resultActions = mvc.perform(
                mvcPost("/api/v1/applications/{postId}", expiredMatchedPost.id)
                        .cookie(secondEscortCookie)
        ).andDo(print())

        resultActions
                .andExpect(handler().handlerType(ApplicationController::class.java))
                .andExpect(handler().methodName("apply"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("400-21"))
                .andExpect(jsonPath("$.msg").value("모집 중인 공고에만 지원할 수 있습니다."))
    }
}
