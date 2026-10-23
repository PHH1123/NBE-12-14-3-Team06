package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * 위치(saveLocation)로 동행 진행 단계가 자동 변경되는지 검증한다.
 * 기준: 병원 반경 200m, 집(pickup) 반경 100m, 정확도 50m 초과면 판단하지 않음 (application.yaml 의 escort.geofence)
 *
 * 좌표 감각: 위도 0.001 ≈ 111m, 0.01 ≈ 1.1km
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationEscortAutoProgressServiceTest {

    @field:Autowired private lateinit var applicationService: ApplicationService
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var escortProgressLogRepository: EscortProgressLogRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var rideRepository: RideRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var passwordEncoder: PasswordEncoder

    companion object {
        private val SEQ = AtomicInteger()
        // 기준 좌표 (newPost 의 병원, 집 좌표와 같다)
        private val HOSPITAL_LAT = BigDecimal.valueOf(37.5665)
        private val HOSPITAL_LNG = BigDecimal.valueOf(126.9780)
        private val HOME_LAT = BigDecimal.valueOf(37.5700)
        private val HOME_LNG = BigDecimal.valueOf(126.9800)
        private val GOOD_ACCURACY = BigDecimal.valueOf(10)
        private val BAD_ACCURACY = BigDecimal.valueOf(80)
    }

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var post: Post
    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        val seq = SEQ.incrementAndGet()
        client = saveUser("autoClient$seq", Role.CLIENT, String.format("010-6661-%04d", seq))
        escort = saveUser("autoEscort$seq", Role.ESCORT, String.format("010-6662-%04d", seq))

        // 진행 중인 공고 + 승인된 지원 + 이동수단(병원행, 귀가)
        post = postWithStatus(client, PostStatus.IN_PROGRESS)
        application = applicationRepository.save(Application.builder().post(post).escort(escort).build())
        application.accept()
        application = applicationRepository.save(application)

        rideRepository.save(Ride.builder().post(post).build())
        rideRepository.save(Ride.builder().post(post).direction(RideDirection.TO_HOME).build())
    }

    private fun saveUser(username: String, role: Role, phone: String): User {
        return userRepository.save(User(username, passwordEncoder.encode("testPassword"),
            "$username@test.com", "테스트", role, Gender.MALE, LocalDate.of(1990, 1, 1), phone, "서울"))
    }

    private fun newPost(owner: User): Post {
        return postRepository.save(Post.builder()
            .client(owner)
            .title("취소 테스트 공고").content("내용").region("서울")
            .hospitalName("테스트 병원").hospitalAddress("서울시")
            .hospitalLat(HOSPITAL_LAT).hospitalLng(HOSPITAL_LNG)
            .pickupAddress("서울시").pickupLat(HOME_LAT).pickupLng(HOME_LNG)
            .hourlyPay(15000)
            .recruitStartAt(LocalDateTime.now()).recruitEndAt(LocalDateTime.now().plusDays(1))
            .escortStartAt(LocalDateTime.now().plusDays(2))
            .escortEndAt(LocalDateTime.now().plusDays(2).plusHours(3))
            .patientNote("테스트").reportRequired(false)
            .build())
    }

    private fun postWithStatus(owner: User, status: PostStatus): Post {
        val post = newPost(owner)
        when (status) {
            PostStatus.OPEN -> Unit
            PostStatus.MATCHED -> post.match()
            PostStatus.IN_PROGRESS -> { post.match(); post.startProgress(LocalDateTime.now()) }
            PostStatus.COMPLETED -> { post.match(); post.startProgress(LocalDateTime.now().minusHours(1)); post.complete(LocalDateTime.now()) }
            PostStatus.CANCELED -> post.matchedCancel()
            PostStatus.EXPIRED -> post.expire()
        }
        return postRepository.save(post)
    }

    private fun statusOf(post: Post): PostStatus = postRepository.findById(post.id!!).orElseThrow().postStatus

    // ===== 준비·확인 도우미 =====

    // 지정한 단계까지 진행한 상태로 만든다. 실제 흐름처럼 단계별 로그와 이동수단 상태도 맞춘다
    private fun givenProgress(target: EscortProgress) {
        val base = LocalDateTime.now().minusHours(1)
        var minute = 0
        for (progress in EscortProgress.values()) {
            if (progress == EscortProgress.NOT_STARTED) continue
            escortProgressLogRepository.save(EscortProgressLog.builder()
                .application(application)
                .progress(progress)
                .occurredAt(base.plusMinutes((minute++).toLong()))
                .source(EscortProgressLog.ProgressSource.BUTTON)
                .build())
            if (progress == target) break
        }

        // 병원행은 "병원 이동 중"부터 이동 중, "병원 도착" 이후 완료. 귀가는 "귀가 중"부터 이동 중
        when (target) {
            EscortProgress.TO_HOSPITAL -> changeRide(RideDirection.TO_HOSPITAL, RideStatus.IN_PROGRESS)
            EscortProgress.TO_HOME -> {
                changeRide(RideDirection.TO_HOSPITAL, RideStatus.IN_PROGRESS, RideStatus.COMPLETED)
                changeRide(RideDirection.TO_HOME, RideStatus.IN_PROGRESS)
            }
            else -> Unit
        }
    }

    private fun changeRide(direction: RideDirection, vararg steps: RideStatus) {
        val ride = rideOf(direction)
        for (step in steps) ride.updateStatus(step)
        rideRepository.save(ride)
    }

    private fun rideOf(direction: RideDirection): Ride = rideRepository.findByPostId(post.id).stream()
        .filter { ride -> ride.direction == direction }
        .findFirst().orElseThrow()

    private fun at(baseLat: BigDecimal, baseLng: BigDecimal, latOffset: Double, accuracy: BigDecimal): ApplicationLocationRequest =
        ApplicationLocationRequest(baseLat.add(BigDecimal.valueOf(latOffset)), baseLng, accuracy)

    private fun sendLocation(request: ApplicationLocationRequest) {
        applicationService.saveLocation(application.id, escort.id, request)
    }

    private fun currentProgress(): EscortProgress = escortProgressLogRepository
        .findTopByApplicationOrderByOccurredAtDescIdDesc(application)
        .map { log -> log.progress }
        .orElse(EscortProgress.NOT_STARTED) ?: EscortProgress.NOT_STARTED

    // ===== 병원 도착 (TO_HOSPITAL -> AT_HOSPITAL) =====

    @Test
    @DisplayName("병원 이동 중에 병원 반경 안 위치를 보내면 병원 도착으로 자동 변경되고, 병원행 이동이 완료된다")
    fun arriveHospital_autoChange() {
        givenProgress(EscortProgress.TO_HOSPITAL)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.AT_HOSPITAL)
        assertThat(rideOf(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.COMPLETED)
    }

    @Test
    @DisplayName("병원 이동 중이어도 병원에서 먼 위치를 보내면 변경되지 않는다")
    fun arriveHospital_farAway_noChange() {
        givenProgress(EscortProgress.TO_HOSPITAL)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.01, GOOD_ACCURACY)) // 0.01 = 1.1km
        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL)
        assertThat(rideOf(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.IN_PROGRESS)
    }

    @Test
    @DisplayName("위치 정확도가 기준(50m)보다 나쁘면 병원 반경 안이어도 변경되지 않는다")
    fun arriveHospital_badAccuracy_noChange() {
        givenProgress(EscortProgress.TO_HOSPITAL)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, BAD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL)
    }

    // ===== 출발 (DEPARTED -> TO_HOSPITAL) =====

    @Test
    @DisplayName("출발 상태에서 직전 위치가 집 반경 안이고 지금 위치가 밖이면 병원 이동 중으로 변경되고, 병원행 이동이 시작된다")
    fun leavePickup_autoChange() {
        givenProgress(EscortProgress.DEPARTED)
        sendLocation(at(HOME_LAT, HOME_LNG, 0.0005, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED)
        sendLocation(at(HOME_LAT, HOME_LNG, 0.003, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL)
        assertThat(rideOf(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.IN_PROGRESS)
    }

    @Test
    @DisplayName("출발 상태에서 계속 집 반경 밖에 있으면(집으로 오는 중) 변경되지 않는다")
    fun leavePickup_stillFarAway_noChange() {
        givenProgress(EscortProgress.DEPARTED)
        sendLocation(at(HOME_LAT, HOME_LNG, 0.01, GOOD_ACCURACY))
        sendLocation(at(HOME_LAT, HOME_LNG, 0.009, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED)
    }

    // ===== 귀가 (TO_HOME -> ARRIVED_HOME) =====

    @Test
    @DisplayName("귀가 중에 집 반경 안 위치를 보내면 귀가 완료로 자동 변경된다")
    fun arriveHome_autoChange() {
        givenProgress(EscortProgress.TO_HOME)
        sendLocation(at(HOME_LAT, HOME_LNG, 0.0005, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.ARRIVED_HOME)
        assertThat(rideOf(RideDirection.TO_HOME).rideStatus).isEqualTo(RideStatus.COMPLETED)
    }

    // ===== 공통 규칙 =====

    @Test
    @DisplayName("단계를 건너뛰지 않는다 (출발 상태에서 병원 근처 위치를 보내도 변경되지 않는다)")
    fun doNotSkipStep() {
        givenProgress(EscortProgress.DEPARTED)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.0005, GOOD_ACCURACY))
        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED)
        assertThat(rideOf(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.ACCEPTED)
    }

    @Test
    @DisplayName("자동 변경된 로그에는 변경 방식이 AUTO_GPS로, 버튼으로 변경한 로그에는 BUTTON으로 남는다")
    fun recordSource() {
        givenProgress(EscortProgress.TO_HOSPITAL)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY))
        applicationService.updateProgress(application.id, escort.id, EscortProgress.TO_HOME)
        val autoLog = escortProgressLogRepository.findByApplicationAndProgress(application, EscortProgress.AT_HOSPITAL).orElseThrow()
        val buttonLog = escortProgressLogRepository.findByApplicationAndProgress(application, EscortProgress.TO_HOME).orElseThrow()
        assertThat(autoLog.source).isEqualTo(EscortProgressLog.ProgressSource.AUTO_GPS)
        assertThat(buttonLog.source).isEqualTo(EscortProgressLog.ProgressSource.BUTTON)
    }

    @Test
    @DisplayName("버튼으로 이미 같은 단계로 바꾼 뒤 같은 위치가 들어와도 예외 없이 무시된다")
    fun alreadyChangedByButton_ignored() {
        givenProgress(EscortProgress.TO_HOSPITAL)
        applicationService.updateProgress(application.id, escort.id, EscortProgress.AT_HOSPITAL)
        assertThatCode { sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY)) }
            .doesNotThrowAnyException()
        assertThat(currentProgress()).isEqualTo(EscortProgress.AT_HOSPITAL)
        assertThat(escortProgressLogRepository.findAll().stream()
            .filter { log -> log.application!!.id == application.id }
            .filter { log -> log.progress == EscortProgress.AT_HOSPITAL }
            .count()).isEqualTo(1L)
    }
}
