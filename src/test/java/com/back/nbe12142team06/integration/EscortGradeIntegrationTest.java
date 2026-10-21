package com.back.nbe12142team06.integration;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog;
import com.back.nbe12142team06.domain.application.enums.EscortProgress;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository;
import com.back.nbe12142team06.domain.application.service.ApplicationService;
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient;
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository;
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.service.PostService;
import com.back.nbe12142team06.domain.ride.entity.RideSelect;
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest;
import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.EscortGrade;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.EscortGradeHistoryRepository;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등급 통합 테스트: 실제 서비스 흐름(동행 완료, 노쇼 취소)에서 등급과 이력이 갱신되는지 확인한다.
 * - 단위 테스트(EscortGradeServiceTest)는 refreshGrade 를 직접 부르지만, 여기서는 PostService / ApplicationService 가
 *   refreshGrade 를 "제대로 호출하는지"까지 이어서 확인한다.
 * - 테스트마다 트랜잭션이 롤백되어 데이터가 남지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EscortGradeIntegrationTest {

    // 시급, 동행 시간 (결제 금액 = 시급 * 시간)
    private static final int HOURLY_PAY = 15_000;
    private static final int HOURS = 4;

    @Autowired
    private UserService userService;
    @Autowired
    private PostService postService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EscortProfileRepository escortProfileRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private EscortProgressLogRepository escortProgressLogRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private EscortGradeHistoryRepository escortGradeHistoryRepository;

    // 외부 결제 API 는 호출하지 않는다
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    private User client;
    private User escort;

    // 의뢰인, 동행 매니저(완료/노쇼 횟수와 저장된 등급을 원하는 값으로)를 만든다.
    private void prepareUsers(int completedCount, int noShowCount, EscortGrade storedGrade) {
        client = userService.signUp(new UserSignUpRequest(
                "gradeClient", "testPassword1!", "gradeClient@test.test", "김의뢰", Role.CLIENT,
                Gender.MALE, LocalDate.of(1990, 5, 20), "010-5551-0001", "서울시"));
        escort = userService.signUp(new UserSignUpRequest(
                "gradeEscortUser", "testPassword2!", "gradeEscort@test.test", "최동행", Role.ESCORT,
                Gender.MALE, LocalDate.of(1990, 5, 20), "010-5551-0002", "서울시"));

        EscortProfile profile = new EscortProfile(escort, "소개", "은행", escort.getName(), "000-1234567-000");
        ReflectionTestUtils.setField(profile, "completedCount", completedCount);
        ReflectionTestUtils.setField(profile, "noShowCount", noShowCount);
        profile.updateGrade(storedGrade);
        escortProfileRepository.save(profile);
        escortProfileRepository.verify(escort.getId(), LocalDateTime.now());   // 교육 이수 처리 (지원 가능 상태)
    }

    // 공고 작성 → 결제 완료 처리 → 지원 → 승인까지 해서 "매칭된 지원"을 만든다.
    private Application matchedApplication() {
        PostWriteRequest request = new PostWriteRequest(
                "정형외과 동행 구합니다", "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.", "서울",
                "서울성모병원", "서울 서초구 반포대로 222",
                BigDecimal.valueOf(37.5012743), BigDecimal.valueOf(127.0051893),
                "서울 서초구 잠원동 10-1", BigDecimal.valueOf(37.5160000), BigDecimal.valueOf(127.0200000),
                HOURLY_PAY,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(6),     // 모집 기간
                LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(7).plusHours(HOURS),   // 동행 시간
                RideSelect.TAXI, RideSelect.TAXI, "", true);

        Post post = postService.findById(postService.write(client.getId(), request).id());
        paymentRepository.testStatusDone(post.getId());   // 결제 완료 상태로 만든다
        // 공고 다시 조회
        post = postService.findById(post.getId());

        ReflectionTestUtils.setField(post, "recruitStartAt", LocalDateTime.now().minusMinutes(1));
        Long applicationId = applicationService.apply(post.getId(), escort.getId()).id();
        applicationService.accept(applicationId, client.getId());

        return applicationRepository.findAllByPostIdWithEscort(post.getId()).stream().findFirst().orElseThrow();
    }

    private EscortProfile profileOfEscort() {
        return escortProfileRepository.findById(escort.getId()).orElseThrow();
    }

    private List<EscortGradeHistory> historiesOfEscort() {
        return escortGradeHistoryRepository.findAllByEscortProfileUserId(escort.getId());
    }

    @Test
    @DisplayName("[통합] 동행 완료로 완료 5회가 되면 등급이 새싹으로 오르고 이력이 남는다")
    void 동행완료로_5회가_되면_승급한다() {
        // 완료 4회(아직 씨앗)인 동행인
        prepareUsers(4, 0, EscortGrade.SEED);
        Application application = matchedApplication();

        // 동행 시작 → 귀가 완료 기록 (escortComplete 가 이 시각들을 읽는다)
        LocalDateTime departedAt = LocalDateTime.now();
        application.getPost().startProgress(departedAt);   // 공고를 동행 진행 중으로
        escortProgressLogRepository.save(EscortProgressLog.builder()
                .application(application).progress(EscortProgress.DEPARTED).occurredAt(departedAt).build());
        escortProgressLogRepository.save(EscortProgressLog.builder()
                .application(application).progress(EscortProgress.ARRIVED_HOME).occurredAt(departedAt.plusHours(HOURS)).build());

        // 동행 완료 처리 (완료 횟수 4 → 5)
        postService.escortComplete(application.getPost().getId(), client.getId());

        EscortProfile after = profileOfEscort();
        assertThat(after.getCompletedCount()).isEqualTo(5);
        assertThat(after.getGrade()).isEqualTo(EscortGrade.SPROUT);

        List<EscortGradeHistory> histories = historiesOfEscort();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getPreviousGrade()).isEqualTo(EscortGrade.SEED);
        assertThat(histories.get(0).getNewGrade()).isEqualTo(EscortGrade.SPROUT);
    }

    @Test
    @DisplayName("[통합] 승인 후 동행인이 취소(노쇼)해 노쇼 2회가 되면 등급이 한 단계 내려가고 이력이 남는다")
    void 노쇼_2회가_되면_강등한다() {
        // 완료 10회(꽃), 노쇼 1회인 동행인
        prepareUsers(10, 1, EscortGrade.FLOWER);
        Application application = matchedApplication();

        // 승인된 지원을 동행인이 취소 → 노쇼 처리 (노쇼 1 → 2)
        applicationService.cancel(application.getId(), escort.getId());

        EscortProfile after = profileOfEscort();
        assertThat(after.getNoShowCount()).isEqualTo(2);
        assertThat(after.getGrade()).isEqualTo(EscortGrade.SPROUT);   // 완료 10회지만 노쇼 2회라 한 단계 강등

        List<EscortGradeHistory> histories = historiesOfEscort();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getPreviousGrade()).isEqualTo(EscortGrade.FLOWER);
        assertThat(histories.get(0).getNewGrade()).isEqualTo(EscortGrade.SPROUT);
    }
}
