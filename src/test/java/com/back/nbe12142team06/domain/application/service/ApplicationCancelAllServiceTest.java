
package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.payment.service.PaymentService;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationCancelAllServiceTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EscortProfileRepository escortProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PaymentService paymentService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private User client;
    private User escort;

    @BeforeEach
    void setUp() {
        int seq = SEQ.incrementAndGet();

        client = saveUser("testClient" + seq, Role.CLIENT,
                String.format("010-7001-%04d", seq));

        escort = saveUser("testEscort" + seq, Role.ESCORT,
                String.format("010-7002-%04d", seq));
    }

    private User saveUser(String username, Role role, String phone) {
        return userRepository.save(new User(
                username,
                passwordEncoder.encode("testPassword"),
                username + "@test.com",
                "테스트",
                role,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                phone,
                "서울"
        ));
    }

    private Post newPost(User owner) {
        return postRepository.save(Post.builder()
                .client(owner)
                .title("지원취소 테스트 공고")
                .content("내용")
                .region("서울")
                .hospitalName("테스트 병원")
                .hospitalAddress("서울시")
                .hospitalLat(BigDecimal.valueOf(37.5665))
                .hospitalLng(BigDecimal.valueOf(126.9780))
                .pickupAddress("서울시")
                .pickupLat(BigDecimal.valueOf(37.5700))
                .pickupLng(BigDecimal.valueOf(126.9800))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now())
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(LocalDateTime.now().plusDays(2))
                .escortEndAt(LocalDateTime.now().plusDays(2).plusHours(3))
                .patientNote("테스트")
                .reportRequired(false)
                .build());
    }

    @Test
    @DisplayName("PENDING 지원은 패널티 없이 취소되고 공고는 OPEN으로 유지된다")
    void 대기중인_지원은_패널티없이_취소된다() {

        // given: 모집 중 공고와 대기 중인 지원 생성
        Post post = newPost(client);

        Application application = applicationRepository.save(
                Application.builder()
                        .post(post)
                        .escort(escort)
                        .build()
        );

        // when: 회원 탈퇴·정지용 일괄 지원취소 실행
        applicationService.cancelAllByEscort(escort.getId());

        // then: 지원은 취소되고 공고는 그대로 모집 중
        Application result = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        Post resultPost = postRepository
                .findById(post.getId())
                .orElseThrow();

        assertThat(result.getStatus())
                .isEqualTo(ApplicationStatus.CANCELED);

        assertThat(result.getActivePostId()).isNull();

        assertThat(resultPost.getPostStatus())
                .isEqualTo(PostStatus.OPEN);

        // PENDING 취소에서는 환불이 호출되지 않아야 함
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("ACCEPTED 지원은 패널티 없이 취소되고 MATCHED 공고도 취소된다")
    void 승인된_지원은_패널티없이_취소된다() {

        // given: 지원이 승인되어 공고가 매칭된 상황
        Post post = newPost(client);
        post.match();
        postRepository.save(post);

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        application.accept();
        application = applicationRepository.save(application);

        // when: 탈퇴·정지로 인한 일괄 지원취소
        applicationService.cancelAllByEscort(escort.getId());

        // then: 지원 상태 검증
        Application result = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        Post resultPost = postRepository
                .findById(post.getId())
                .orElseThrow();

        assertThat(result.getStatus())
                .isEqualTo(ApplicationStatus.CANCELED);

        assertThat(result.getAcceptedPostId()).isNull();
        assertThat(result.getActivePostId()).isNull();

        // 매칭된 공고도 취소되었는지 확인
        assertThat(resultPost.getPostStatus())
                .isEqualTo(PostStatus.CANCELED);

        // 환불 서비스가 정확히 한 번 호출되었는지 검증
        verify(paymentService, times(1))
                .cancel(argThat(p -> p.getId().equals(post.getId())));
    }


    @Test
    @DisplayName("동행 진행 중인 지원은 일괄 취소 대상에서 제외된다")
    void 진행중인_동행은_취소되지_않는다() {

        // given: 승인된 지원과 진행 중인 공고
        Post post = newPost(client);
        post.match();
        post.startProgress(LocalDateTime.now());
        postRepository.save(post);

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        application.accept();
        application = applicationRepository.save(application);

        // when: 탈퇴·정지용 지원 일괄 취소
        applicationService.cancelAllByEscort(escort.getId());

        // then: 기존 상태가 그대로 유지되는지 확인
        Application result = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        Post resultPost = postRepository
                .findById(post.getId())
                .orElseThrow();

        assertThat(result.getStatus())
                .isEqualTo(ApplicationStatus.ACCEPTED);

        assertThat(resultPost.getPostStatus())
                .isEqualTo(PostStatus.IN_PROGRESS);

        assertThat(result.getAcceptedPostId())
                .isEqualTo(post.getId());

        // 진행 중인 공고는 환불 요청도 없어야 함
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("회원 탈퇴·정지에 의한 지원취소는 노쇼 횟수를 증가시키지 않는다")
    void 일괄취소시_노쇼횟수는_증가하지_않는다() {

        // given: 노쇼 횟수가 이미 2회인 동행매니저
        EscortProfile escortProfile = new EscortProfile(
                escort,
                "테스트 동행매니저",
                "테스트은행",
                "테스트",
                "123-456-789"
        );

        escortProfile.increaseNoShowCount();
        escortProfile.increaseNoShowCount();
        escortProfileRepository.save(escortProfile);

        // 승인된 지원과 매칭된 공고 생성
        Post post = newPost(client);
        post.match();
        postRepository.save(post);

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        application.accept();
        applicationRepository.save(application);

        // when: 회원 탈퇴·정지용 패널티 없는 지원취소 실행
        applicationService.cancelAllByEscort(escort.getId());

        // then: 노쇼 횟수가 2회로 유지되는지 확인
        EscortProfile result = escortProfileRepository
                .findById(escort.getId())
                .orElseThrow();

        assertThat(result.getNoShowCount()).isEqualTo(2);

        // 지원은 정상 취소되어야 함
        Application canceledApplication = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        assertThat(canceledApplication.getStatus())
                .isEqualTo(ApplicationStatus.CANCELED);
    }


    @Test
    @DisplayName("다른 동행매니저의 지원은 일괄 취소되지 않는다")
    void 다른_동행매니저의_지원은_유지된다() {

        // given
        User otherEscort = saveUser(
                "otherEscort" + SEQ.incrementAndGet(),
                Role.ESCORT,
                "010-8000-" + String.format("%04d", SEQ.get())
        );

        Post myPost = newPost(client);
        Post otherPost = newPost(client);

        Application myApplication = applicationRepository.save(
                Application.builder()
                        .post(myPost)
                        .escort(escort)
                        .build()
        );

        Application otherApplication = applicationRepository.save(
                Application.builder()
                        .post(otherPost)
                        .escort(otherEscort)
                        .build()
        );

        // when
        applicationService.cancelAllByEscort(escort.getId());

        // then
        assertThat(applicationRepository.findById(myApplication.getId())
                .orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CANCELED);

        assertThat(applicationRepository.findById(otherApplication.getId())
                .orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("취소 가능한 지원이 없는 경우 정상 종료된다")
    void 취소할_지원이_없으면_정상종료된다() {

        // when
        applicationService.cancelAllByEscort(escort.getId());

        // then: 예외 없이 종료되고 환불 호출도 없어야 한다
        verifyNoInteractions(paymentService);
    }

}
