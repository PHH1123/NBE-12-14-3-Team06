package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest;
import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog;
import com.back.nbe12142team06.domain.application.enums.EscortProgress;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.ride.entity.Ride;
import com.back.nbe12142team06.domain.ride.entity.RideDirection;
import com.back.nbe12142team06.domain.ride.entity.RideStatus;
import com.back.nbe12142team06.domain.ride.repository.RideRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 위치(saveLocation)로 동행 진행 단계가 자동 변경되는지 검증한다.
 * 기준: 병원 반경 200m, 집(pickup) 반경 100m, 정확도 50m 초과면 판단하지 않음 (application.yaml 의 escort.geofence)
 *
 * 좌표 감각: 위도 0.001 ≈ 111m, 0.01 ≈ 1.1km
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationEscortAutoProgressServiceTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EscortProgressLogRepository escortProgressLogRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EscortProfileRepository escortProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    private static final AtomicInteger SEQ = new AtomicInteger();

    // 기준 좌표 (newPost 의 병원, 집 좌표와 같다)
    private static final BigDecimal HOSPITAL_LAT = BigDecimal.valueOf(37.5665);
    private static final BigDecimal HOSPITAL_LNG = BigDecimal.valueOf(126.9780);
    private static final BigDecimal HOME_LAT = BigDecimal.valueOf(37.5700);
    private static final BigDecimal HOME_LNG = BigDecimal.valueOf(126.9800);

    private static final BigDecimal GOOD_ACCURACY = BigDecimal.valueOf(10);
    private static final BigDecimal BAD_ACCURACY = BigDecimal.valueOf(80);

    private User client;
    private User escort;
    private Post post;
    private Application application;

    @BeforeEach
    void setUp() {
        int seq = SEQ.incrementAndGet();
        client = saveUser("autoClient" + seq, Role.CLIENT, String.format("010-6661-%04d", seq));
        escort = saveUser("autoEscort" + seq, Role.ESCORT, String.format("010-6662-%04d", seq));

        // 진행 중인 공고 + 승인된 지원 + 이동수단(병원행, 귀가)
        post = postWithStatus(client, PostStatus.IN_PROGRESS);

        application = applicationRepository.save(Application.builder().post(post).escort(escort).build());
        application.accept();
        application = applicationRepository.save(application);

        rideRepository.save(Ride.builder().post(post).build());
        rideRepository.save(Ride.builder().post(post).direction(RideDirection.TO_HOME).build());
    }

    private User saveUser(String username, Role role, String phone) {
        return userRepository.save(new User(
                username, passwordEncoder.encode("testPassword"), username + "@test.com", "테스트",
                role, Gender.MALE, LocalDate.of(1990, 1, 1), phone, "서울"));
    }

    private Post newPost(User owner) {
        return postRepository.save(Post.builder()
                .client(owner)
                .title("취소 테스트 공고").content("내용").region("서울")
                .hospitalName("테스트 병원").hospitalAddress("서울시")
                .hospitalLat(HOSPITAL_LAT).hospitalLng(HOSPITAL_LNG)
                .pickupAddress("서울시")
                .pickupLat(HOME_LAT).pickupLng(HOME_LNG)
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now()).recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(LocalDateTime.now().plusDays(2))
                .escortEndAt(LocalDateTime.now().plusDays(2).plusHours(3))
                .patientNote("테스트").reportRequired(false)
                .build());
    }

    private Post postWithStatus(User owner, PostStatus status) {
        Post post = newPost(owner);
        switch (status) {
            case OPEN -> { }
            case MATCHED -> post.match();
            case IN_PROGRESS -> { post.match(); post.startProgress(LocalDateTime.now()); }
            case COMPLETED -> { post.match(); post.startProgress(LocalDateTime.now().minusHours(1)); post.complete(LocalDateTime.now()); }
            case CANCELED -> post.matchedCancel();
            case EXPIRED -> post.expire();
        }
        return postRepository.save(post);
    }

    private PostStatus statusOf(Post post) {
        return postRepository.findById(post.getId()).orElseThrow().getPostStatus();
    }

    // ===== 준비·확인 도우미 =====

    // 지정한 단계까지 진행한 상태로 만든다. 실제 흐름처럼 단계별 로그와 이동수단 상태도 맞춘다
    private void givenProgress(EscortProgress target) {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        int minute = 0;
        for (EscortProgress progress : EscortProgress.values()) {
            if (progress == EscortProgress.NOT_STARTED) {
                continue;
            }
            escortProgressLogRepository.save(EscortProgressLog.builder()
                    .application(application)
                    .progress(progress)
                    .occurredAt(base.plusMinutes(minute++))
                    .source(EscortProgressLog.ProgressSource.BUTTON)
                    .build());
            if (progress == target) {
                break;
            }
        }

        // 병원행은 "병원 이동 중"부터 이동 중, "병원 도착" 이후 완료. 귀가는 "귀가 중"부터 이동 중
        switch (target) {
            case TO_HOSPITAL -> changeRide(RideDirection.TO_HOSPITAL, RideStatus.IN_PROGRESS);
            case TO_HOME -> {
                changeRide(RideDirection.TO_HOSPITAL, RideStatus.IN_PROGRESS, RideStatus.COMPLETED);
                changeRide(RideDirection.TO_HOME, RideStatus.IN_PROGRESS);
            }
            default -> { }
        }
    }

    private void changeRide(RideDirection direction, RideStatus... steps) {
        Ride ride = rideOf(direction);
        for (RideStatus step : steps) {
            ride.updateStatus(step);
        }
        rideRepository.save(ride);
    }

    private Ride rideOf(RideDirection direction) {
        return rideRepository.findByPostId(post.getId()).stream()
                .filter(ride -> ride.getDirection() == direction)
                .findFirst()
                .orElseThrow();
    }

    private ApplicationLocationRequest at(BigDecimal baseLat, BigDecimal baseLng, double latOffset, BigDecimal accuracy) {
        return new ApplicationLocationRequest(baseLat.add(BigDecimal.valueOf(latOffset)), baseLng, accuracy);
    }

    private void sendLocation(ApplicationLocationRequest request) {
        applicationService.saveLocation(application.getId(), escort.getId(), request);
    }

    private EscortProgress currentProgress() {
        return escortProgressLogRepository
                .findTopByApplicationOrderByOccurredAtDescIdDesc(application)
                .map(EscortProgressLog::getProgress)
                .orElse(EscortProgress.NOT_STARTED);
    }

    // ===== 병원 도착 (TO_HOSPITAL -> AT_HOSPITAL) =====

    @Test
    @DisplayName("병원 이동 중에 병원 반경 안 위치를 보내면 병원 도착으로 자동 변경되고, 병원행 이동이 완료된다")
    void arriveHospital_autoChange() {
        // given: 병원 이동 중
        givenProgress(EscortProgress.TO_HOSPITAL);

        // when: 병원에서 약 111m 떨어진 위치 (반경 200m 안)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY));

        // then
        assertThat(currentProgress()).isEqualTo(EscortProgress.AT_HOSPITAL);
        assertThat(rideOf(RideDirection.TO_HOSPITAL).getRideStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    @DisplayName("병원 이동 중이어도 병원에서 먼 위치를 보내면 변경되지 않는다")
    void arriveHospital_farAway_noChange() {
        givenProgress(EscortProgress.TO_HOSPITAL);

        // 병원에서 약 1.1km 떨어진 위치
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.01, GOOD_ACCURACY)); // 0.01 = 1.1km

        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL);
        assertThat(rideOf(RideDirection.TO_HOSPITAL).getRideStatus()).isEqualTo(RideStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("위치 정확도가 기준(50m)보다 나쁘면 병원 반경 안이어도 변경되지 않는다")
    void arriveHospital_badAccuracy_noChange() {
        givenProgress(EscortProgress.TO_HOSPITAL);

        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, BAD_ACCURACY));

        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL);
    }

    // ===== 출발 (DEPARTED -> TO_HOSPITAL) =====

    @Test
    @DisplayName("출발 상태에서 직전 위치가 집 반경 안이고 지금 위치가 밖이면 병원 이동 중으로 변경되고, 병원행 이동이 시작된다")
    void leavePickup_autoChange() {
        givenProgress(EscortProgress.DEPARTED);

        // 집 도착: 약 55m (반경 100m 안). 직전 위치가 아직 없으므로 변경되지 않는다
        sendLocation(at(HOME_LAT, HOME_LNG, 0.0005, GOOD_ACCURACY));
        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED);

        // 환자를 태우고 집에서 약 333m 벗어남 (반경 밖)
        sendLocation(at(HOME_LAT, HOME_LNG, 0.003, GOOD_ACCURACY));

        assertThat(currentProgress()).isEqualTo(EscortProgress.TO_HOSPITAL);
        assertThat(rideOf(RideDirection.TO_HOSPITAL).getRideStatus()).isEqualTo(RideStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("출발 상태에서 계속 집 반경 밖에 있으면(집으로 오는 중) 변경되지 않는다")
    void leavePickup_stillFarAway_noChange() {
        givenProgress(EscortProgress.DEPARTED);

        // 집에서 먼 곳(약 1.1km, 약 1.0km)을 연달아 보낸다
        sendLocation(at(HOME_LAT, HOME_LNG, 0.01, GOOD_ACCURACY));
        sendLocation(at(HOME_LAT, HOME_LNG, 0.009, GOOD_ACCURACY));

        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED);
    }

    // ===== 귀가 (TO_HOME -> ARRIVED_HOME) =====

    @Test
    @DisplayName("귀가 중에 집 반경 안 위치를 보내면 귀가 완료로 자동 변경된다")
    void arriveHome_autoChange() {
        givenProgress(EscortProgress.TO_HOME);

        sendLocation(at(HOME_LAT, HOME_LNG, 0.0005, GOOD_ACCURACY));

        assertThat(currentProgress()).isEqualTo(EscortProgress.ARRIVED_HOME);
        assertThat(rideOf(RideDirection.TO_HOME).getRideStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    // ===== 공통 규칙 =====

    @Test
    @DisplayName("단계를 건너뛰지 않는다 (출발 상태에서 병원 근처 위치를 보내도 변경되지 않는다)")
    void doNotSkipStep() {
        givenProgress(EscortProgress.DEPARTED);

        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.0005, GOOD_ACCURACY));

        assertThat(currentProgress()).isEqualTo(EscortProgress.DEPARTED);
        assertThat(rideOf(RideDirection.TO_HOSPITAL).getRideStatus()).isEqualTo(RideStatus.ACCEPTED);
    }

    @Test
    @DisplayName("자동 변경된 로그에는 변경 방식이 AUTO_GPS로, 버튼으로 변경한 로그에는 BUTTON으로 남는다")
    void recordSource() {
        givenProgress(EscortProgress.TO_HOSPITAL);

        // 위치로 병원 도착 (자동)
        sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY));
        // 버튼으로 귀가 시작
        applicationService.updateProgress(application.getId(), escort.getId(), EscortProgress.TO_HOME);

        EscortProgressLog autoLog = escortProgressLogRepository
                .findByApplicationAndProgress(application, EscortProgress.AT_HOSPITAL).orElseThrow();
        EscortProgressLog buttonLog = escortProgressLogRepository
                .findByApplicationAndProgress(application, EscortProgress.TO_HOME).orElseThrow();

        assertThat(autoLog.getSource()).isEqualTo(EscortProgressLog.ProgressSource.AUTO_GPS);
        assertThat(buttonLog.getSource()).isEqualTo(EscortProgressLog.ProgressSource.BUTTON);
    }

    @Test
    @DisplayName("버튼으로 이미 같은 단계로 바꾼 뒤 같은 위치가 들어와도 예외 없이 무시된다")
    void alreadyChangedByButton_ignored() {
        givenProgress(EscortProgress.TO_HOSPITAL);

        // 버튼으로 먼저 병원 도착 처리
        applicationService.updateProgress(application.getId(), escort.getId(), EscortProgress.AT_HOSPITAL);

        // 같은 병원 근처 위치가 뒤늦게 들어와도 예외 없이 처리되고, 단계는 그대로이며 로그도 늘지 않는다
        assertThatCode(() -> sendLocation(at(HOSPITAL_LAT, HOSPITAL_LNG, 0.001, GOOD_ACCURACY)))
                .doesNotThrowAnyException();

        assertThat(currentProgress()).isEqualTo(EscortProgress.AT_HOSPITAL);
        assertThat(escortProgressLogRepository.findAll().stream()
                .filter(log -> log.getApplication().getId().equals(application.getId()))
                .filter(log -> log.getProgress() == EscortProgress.AT_HOSPITAL)
                .count()).isEqualTo(1);
    }
}
