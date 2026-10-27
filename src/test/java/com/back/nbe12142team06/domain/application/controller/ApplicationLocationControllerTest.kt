package com.back.nbe12142team06.domain.application.controller

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 실시간 위치(POST/GET /api/v1/applications/{id}/location)를 컨트롤러부터 실제 DB 까지 검증한다.
 *
 * - 의뢰인과 동행 매니저가 각자 위치를 보내고 서로 조회한다 (동행 중에만)
 * - 두 사람이 반경 100m 안이고 위치가 최신이면 nearby 가 true (출발 버튼 활성화 기준)
 * - 동행이 끝나면 위치가 삭제되고 더 이상 보내거나 조회할 수 없다
 * - 위치로 동행 진행 단계가 자동 변경되지 않는다
 *
 * 트랜잭션 경계(요청마다 commit)와 조건부 UPDATE 가 실제로 동작하는지 보려고 클래스에 @Transactional 을 붙이지 않고,
 * 데이터는 DatabaseCleaner 로 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApplicationLocationControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    private var postId = 0L
    private var applicationId = 0L
    private lateinit var clientCookie: Cookie
    private lateinit var escortCookie: Cookie
    private lateinit var outsiderCookie: Cookie

    // 기준 위치(서울시청). 위도 0.0005 ≈ 55m, 0.002 ≈ 222m
    private val baseLat = BigDecimal("37.5665000")
    private val baseLng = BigDecimal("126.9780000")

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()

        val client = saveUser("locClient", Role.CLIENT, "010-1000-0001")
        val escort = saveUser("locEscort", Role.ESCORT, "010-1000-0002")
        saveUser("locOutsider", Role.CLIENT, "010-1000-0003")

        // 매칭된 공고 + 승인된 지원 (아직 출발 전)
        val matched = Post(
            client = client,
            title = "위치 테스트 공고",
            content = "내용",
            region = "서울",
            hospitalName = "테스트병원",
            hospitalAddress = "서울시",
            hospitalLat = BigDecimal("37.5700000"),
            hospitalLng = BigDecimal("126.9900000"),
            pickupAddress = "서울시 자택",
            pickupLat = baseLat,
            pickupLng = baseLng,
            hourlyPay = 15000,
            recruitStartAt = LocalDateTime.now().minusDays(2),
            recruitEndAt = LocalDateTime.now().minusDays(1),
            escortStartAt = LocalDateTime.now().plusHours(1),
            escortEndAt = LocalDateTime.now().plusHours(4),
        )
        matched.match()
        val savedPost = postRepository.save(matched)
        postId = savedPost.id!!

        val application = Application(savedPost, escort)
        application.accept()
        applicationId = applicationRepository.save(application).id!!

        clientCookie = login("locClient")
        escortCookie = login("locEscort")
        outsiderCookie = login("locOutsider")
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    private fun saveUser(username: String, role: Role, phone: String): User {
        return userRepository.save(
            User(
                username, passwordEncoder.encode("testPassword"), "$username@test.com", "테스트",
                role, Gender.MALE, LocalDate.of(1990, 1, 1), phone, "서울",
            ),
        )
    }

    private fun login(username: String): Cookie {
        return mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username": "$username", "password": "testPassword"}"""),
        ).andReturn().response.getCookie("accessToken")!!
    }

    // 동행 출발: 공고가 진행 중(IN_PROGRESS)이 되고 첫 진행 기록(DEPARTED)이 남는다
    private fun startTrip() {
        val post = postRepository.findById(postId).orElseThrow()
        post.startProgress(LocalDateTime.now())
        postRepository.save(post)
        val application = applicationRepository.findById(applicationId).orElseThrow()
        escortProgressLogRepository.save(EscortProgressLog(application, EscortProgress.DEPARTED, LocalDateTime.now()))
    }

    private fun sendLocation(cookie: Cookie, lat: BigDecimal, lng: BigDecimal, accuracy: String = "10"): ResultActions {
        return mvc.perform(
            post("/api/v1/applications/$applicationId/location")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"lat": $lat, "lng": $lng, "accuracy": $accuracy}"""),
        )
    }

    private fun readLocation(cookie: Cookie): ResultActions {
        return mvc.perform(get("/api/v1/applications/$applicationId/location").cookie(cookie))
    }

    private fun pressProgress(progress: EscortProgress): ResultActions {
        return mvc.perform(
            patch("/api/v1/applications/$applicationId/progress")
                .cookie(escortCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"progress": "$progress"}"""),
        )
    }

    private fun latestProgress(): EscortProgress {
        val application = applicationRepository.findById(applicationId).orElseThrow()
        return escortProgressLogRepository.findTopByApplicationOrderByOccurredAtDescIdDesc(application).get().progress
    }

    private fun dataOf(result: ResultActions): Map<*, *> {
        val body = result.andReturn().response.contentAsString
        return JsonPath.parse(body).read<Map<*, *>>("$.data")
    }

    @Test
    @DisplayName("[위치] 의뢰인과 동행 매니저가 각자 위치를 보내면 서로의 위치와 가까움 여부를 조회한다")
    fun 서로_위치를_보내고_조회한다() {
        startTrip()
        sendLocation(escortCookie, baseLat, baseLng).andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value("200-30"))
        sendLocation(clientCookie, baseLat.add(BigDecimal("0.0005")), baseLng).andExpect(status().isOk())

        // 동행 매니저가 조회: 두 사람의 위치가 모두 보이고, 약 55m 라 가깝다
        readLocation(escortCookie)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-31"))
            .andExpect(jsonPath("$.data.escort.lat").exists())
            .andExpect(jsonPath("$.data.client.lat").exists())
            .andExpect(jsonPath("$.data.nearby").value(true))
            .andExpect(jsonPath("$.data.distanceMeters").value(allOf(greaterThan(40), lessThan(70))))

        // 의뢰인이 조회해도 같은 결과
        readLocation(clientCookie)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.escort.lat").exists())
            .andExpect(jsonPath("$.data.client.lat").exists())
            .andExpect(jsonPath("$.data.nearby").value(true))
    }

    @Test
    @DisplayName("[위치] 한쪽만 위치를 보냈으면 다른 쪽 위치는 null 이고 가깝지 않다")
    fun 한쪽만_보내면_다른쪽은_null이다() {
        startTrip()
        sendLocation(escortCookie, baseLat, baseLng).andExpect(status().isOk())

        val data = dataOf(readLocation(clientCookie).andExpect(status().isOk()))

        assertThat(data["escort"]).isNotNull()
        assertThat(data["client"]).isNull()
        assertThat(data["nearby"]).isEqualTo(false)
        assertThat(data["distanceMeters"]).isNull()
    }

    @Test
    @DisplayName("[위치] 반경 100m 밖이면 가깝지 않다")
    fun 반경_밖이면_가깝지_않다() {
        startTrip()
        sendLocation(escortCookie, baseLat, baseLng).andExpect(status().isOk())
        sendLocation(clientCookie, baseLat.add(BigDecimal("0.002")), baseLng).andExpect(status().isOk())

        readLocation(escortCookie)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nearby").value(false))
            .andExpect(jsonPath("$.data.distanceMeters").value(allOf(greaterThan(200), lessThan(240))))
    }

    @Test
    @DisplayName("[위치] 한쪽 위치가 30초보다 오래됐으면 가까워도 가깝지 않은 것으로 본다")
    fun 위치가_오래되면_가깝지_않다() {
        startTrip()
        sendLocation(escortCookie, baseLat, baseLng).andExpect(status().isOk())
        sendLocation(clientCookie, baseLat.add(BigDecimal("0.0005")), baseLng).andExpect(status().isOk())

        // 의뢰인의 위치가 2분 전에 갱신된 것으로 바꾼다 (같은 조건의 조건부 UPDATE)
        transactionTemplate.executeWithoutResult {
            applicationRepository.updateClientLocation(
                applicationId, baseLat.add(BigDecimal("0.0005")), baseLng, BigDecimal.TEN, Instant.now().minusSeconds(120),
            )
        }

        val data = dataOf(readLocation(escortCookie).andExpect(status().isOk()))

        assertThat(data["nearby"]).isEqualTo(false)
        assertThat(data["distanceMeters"]).isNull()
    }

    @Test
    @DisplayName("[위치] 해당 동행의 의뢰인·동행 매니저가 아니면 위치를 보내거나 조회할 수 없다")
    fun 제3자는_보내지도_조회하지도_못한다() {
        startTrip()

        sendLocation(outsiderCookie, baseLat, baseLng)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-29"))
        readLocation(outsiderCookie)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-30"))
    }

    @Test
    @DisplayName("[위치] 출발 전에는 위치를 보내거나 조회할 수 없다")
    fun 출발_전에는_공유되지_않는다() {
        // startTrip() 을 하지 않음: 공고는 MATCHED 상태
        sendLocation(escortCookie, baseLat, baseLng)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-34"))
        sendLocation(clientCookie, baseLat, baseLng)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-34"))
        readLocation(escortCookie)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-31"))
    }

    @Test
    @DisplayName("[위치] 위도가 범위를 벗어나면 400-01")
    fun 위도_범위를_벗어나면_거절한다() {
        startTrip()

        sendLocation(escortCookie, BigDecimal("91"), baseLng)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
    }

    @Test
    @DisplayName("[위치] 귀가 완료 후에는 두 사람의 위치가 삭제되고 보내기·조회 모두 막힌다")
    fun 동행이_끝나면_위치가_삭제되고_접근할_수_없다() {
        startTrip()
        sendLocation(escortCookie, baseLat, baseLng).andExpect(status().isOk())
        sendLocation(clientCookie, baseLat.add(BigDecimal("0.0005")), baseLng).andExpect(status().isOk())
        assertThat(applicationRepository.findById(applicationId).orElseThrow().clientLat).isNotNull()

        // 동행 매니저가 버튼으로 귀가 완료까지 진행한다
        pressProgress(EscortProgress.TO_HOSPITAL).andExpect(status().isOk())
        pressProgress(EscortProgress.AT_HOSPITAL).andExpect(status().isOk())
        pressProgress(EscortProgress.TO_HOME).andExpect(status().isOk())
        pressProgress(EscortProgress.ARRIVED_HOME).andExpect(status().isOk())

        // 두 사람의 위치가 모두 지워졌다
        val application = applicationRepository.findById(applicationId).orElseThrow()
        assertThat(application.lat).isNull()
        assertThat(application.lng).isNull()
        assertThat(application.locationUpdatedAt).isNull()
        assertThat(application.clientLat).isNull()
        assertThat(application.clientLng).isNull()
        assertThat(application.clientLocationUpdatedAt).isNull()

        // 더 이상 조회할 수도 보낼 수도 없다 (의뢰인, 동행 매니저 모두)
        for (cookie in listOf(escortCookie, clientCookie)) {
            readLocation(cookie).andExpect(status().isNotFound()).andExpect(jsonPath("$.statusCode").value("404-31"))
            sendLocation(cookie, baseLat, baseLng).andExpect(status().isBadRequest()).andExpect(jsonPath("$.statusCode").value("400-34"))
        }
    }

    @Test
    @DisplayName("[위치] 위치가 병원·집 근처여도 진행 단계는 자동으로 바뀌지 않는다 (버튼으로만 변경)")
    fun 위치로는_진행_단계가_바뀌지_않는다() {
        startTrip()
        val post = postRepository.findById(postId).orElseThrow()

        // 출발(DEPARTED) 상태: 집 근처 → 집에서 멀어짐
        sendLocation(escortCookie, post.pickupLat, post.pickupLng).andExpect(status().isOk())
        sendLocation(escortCookie, post.pickupLat.add(BigDecimal("0.01")), post.pickupLng).andExpect(status().isOk())
        assertThat(latestProgress()).isEqualTo(EscortProgress.DEPARTED)

        // 병원 이동 중(TO_HOSPITAL): 병원 좌표 그대로 보내도 병원 도착으로 바뀌지 않는다
        pressProgress(EscortProgress.TO_HOSPITAL).andExpect(status().isOk())
        sendLocation(escortCookie, post.hospitalLat, post.hospitalLng).andExpect(status().isOk())
        assertThat(latestProgress()).isEqualTo(EscortProgress.TO_HOSPITAL)
    }

    @Test
    @DisplayName("[위치] 하트비트 로그는 info 로 남기고 좌표는 남기지 않는다")
    fun 하트비트_로그에는_좌표가_없다() {
        startTrip()
        // 테스트 프로필에는 로그 출력 설정이 없어서, 서비스의 로그 이벤트를 직접 가로챈다
        val serviceLogger = LoggerFactory.getLogger(ApplicationService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = serviceLogger.level
        serviceLogger.level = Level.DEBUG
        serviceLogger.addAppender(appender)

        try {
            sendLocation(escortCookie, BigDecimal("37.5665123"), BigDecimal("126.9780456")).andExpect(status().isOk())
            sendLocation(clientCookie, BigDecimal("37.5670123"), BigDecimal("126.9780456")).andExpect(status().isOk())
        } finally {
            serviceLogger.detachAppender(appender)
            serviceLogger.level = previousLevel
        }

        val heartbeats = appender.list.filter { it.formattedMessage.startsWith("[위치 하트비트") }
        val infos = heartbeats.filter { it.level == Level.INFO }.map { it.formattedMessage }

        assertThat(infos).hasSize(2)                                       // 전송마다 info 한 줄
        assertThat(infos[0]).contains("role=ESCORT")
        assertThat(infos[1]).contains("role=CLIENT").contains("nearby=true")  // 의뢰인이 보낼 때는 상대(동행인)와의 거리도 남는다
        assertThat(heartbeats.any { it.level == Level.DEBUG }).isTrue()     // 상세는 debug
        // 좌표(개인정보)는 어느 로그에도 남기지 않는다
        assertThat(appender.list.map { it.formattedMessage }.joinToString("\n"))
            .doesNotContain("37.5665123").doesNotContain("37.5670123").doesNotContain("126.9780456")
    }
}
