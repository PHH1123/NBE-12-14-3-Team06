package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.enums.EscortProgress;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository;
import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository;
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.post.service.PostService;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.enums.UserStatus;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.service.UserService;
import com.back.nbe12142team06.global.exception.DuplicatedException;
import com.back.nbe12142team06.global.exception.ForbiddenException;
import com.back.nbe12142team06.global.exception.InvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationConcurrencyTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EscortProfileRepository escortProfileRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EscortProgressLogRepository escortProgressLogRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private NoShowPenaltyRepository noShowPenaltyRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    private Long postId;
    private Long escortId;
    private Long clientId;

    private static final AtomicInteger TEST_SEQUENCE = new AtomicInteger();

    @BeforeEach
    void setUp() {

        // 지원을 참조하는 자식 데이터부터 삭제
        noShowPenaltyRepository.deleteAll();
        escortProgressLogRepository.deleteAll();

        // 지원 데이터 삭제
        applicationRepository.deleteAll();

        int seq = TEST_SEQUENCE.incrementAndGet();

        User client = new User(
                "concurrencyClient" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyClient" + seq + "@test.com",
                "의뢰인",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                String.format("010-1111-%04d", seq),
                "수원"
        );

        client = userRepository.save(client);
        clientId = client.getId();

        User escort = new User(
                "concurrencyEscort" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyEscort" + seq + "@test.com",
                "동행인",
                Role.ESCORT,
                Gender.FEMALE,
                LocalDate.of(1995, 1, 1),
                String.format("010-2222-%04d", seq),
                "수원"
        );

        escort = userRepository.save(escort);
        escortId = escort.getId();

        saveVerifiedProfile(escort, "1234");

        Post post = Post.builder()
                .client(client)
                .title("동시성 테스트 공고")
                .content("동시 지원 테스트")
                .region("수원")
                .hospitalName("테스트 병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now())
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(LocalDateTime.now().plusDays(2))
                .escortEndAt(LocalDateTime.now().plusDays(2).plusHours(3))
                .patientNote("테스트")
                .reportRequired(false)
                .build();

        postId = postRepository.save(post).getId();

        saveDonePayment(post);
    }

    private void saveVerifiedProfile(User escort, String accountNumber) {
        escortProfileRepository.save(
                new EscortProfile(escort, "동행인 소개", "테스트은행", escort.getName(), accountNumber));

        transactionTemplate.executeWithoutResult(status ->
                escortProfileRepository.verify(escort.getId(), LocalDateTime.now()));
    }

    /**
     * 결제 완료 상태의 공고를 만든다.
     *
     * 승인된 지원이 정지·탈퇴·공고 취소로 되돌려질 때 결제 환불까지 함께 실행되므로,
     * 결제 키는 TossPaymentClientDev 가 실제 토스 API 를 호출하지 않는 시드 접두어를 쓴다.
     * 접두어가 없으면 환불이 404(NOT_FOUND_PAYMENT)로 실패해 정지·탈퇴 트랜잭션이 롤백된다.
     * 환불 금액은 balanceAmount 에서 가져가므로 결제 금액과 같은 값으로 채워둔다.
     */
    private void saveDonePayment(Post post) {
        paymentRepository.saveAndFlush(Payment.builder()
                .amount(post.getTotalPay().intValue())
                .hourlyPaySnapshot(post.getHourlyPay())
                .hours(post.getEscortHours())
                .post(post)
                .paymentKey("INIT-PAYKEY-concurrency-" + post.getId())
                .balanceAmount(post.getTotalPay().intValue())
                .paymentStatus(PaymentStatus.DONE)
                .build());
    }


    @Test
    @DisplayName("동일한 동행인이 같은 공고에 동시에 지원")
    void concurrentApply() throws InterruptedException {

        int threadCount = 10;

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {

            executorService.submit(() -> {

                readyLatch.countDown();

                try {
                    startLatch.await();

                    applicationService.apply(postId, escortId);

                } catch (Exception e) {
                    System.out.println(
                            Thread.currentThread().getName()
                                    + " 실패: "
                                    + e.getClass().getSimpleName()
                                    + " / "
                                    + e.getMessage()
                    );
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 모든 스레드가 준비될 때까지 대기
        readyLatch.await();

        // 동시에 출발
        startLatch.countDown();

        // 모든 요청 종료까지 대기
        doneLatch.await();

        long count = applicationRepository.count();

        System.out.println("저장된 지원 개수 = " + count);

        assertEquals(1, count);

        executorService.shutdown();
    }

    @Test
    @DisplayName("같은 공고의 서로 다른 지원자를 동시에 승인")
    void concurrentAccept() throws InterruptedException {

        int seq = TEST_SEQUENCE.incrementAndGet();

        User escort2 = new User(
                "concurrencyEscort2_" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyEscort2_" + seq + "@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                String.format("010-3333-%04d", seq),
                "수원"
        );

        escort2 = userRepository.save(escort2);

        saveVerifiedProfile(escort2, "5678");

        // 기존 동행인 조회
        User escort1 = userRepository.findById(escortId)
                .orElseThrow();

        Post post = postRepository.findById(postId)
                .orElseThrow();

        // 같은 공고에 서로 다른 동행인 2명이 지원한 상태 생성
        Application application1 = Application.builder()
                .post(post)
                .escort(escort1)
                .build();

        Application application2 = Application.builder()
                .post(post)
                .escort(escort2)
                .build();

        application1 = applicationRepository.save(application1);
        application2 = applicationRepository.save(application2);

        Long applicationId1 = application1.getId();
        Long applicationId2 = application2.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(2);

        // 지원자 1 승인
        executorService.submit(() -> {

            readyLatch.countDown();

            try {
                startLatch.await();

                applicationService.accept(
                        applicationId1,
                        clientId
                );

            } catch (Exception e) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " 승인 실패: "
                                + e.getClass().getSimpleName()
                                + " / "
                                + e.getMessage()
                );
            } finally {
                doneLatch.countDown();
            }
        });

        // 지원자 2 승인
        executorService.submit(() -> {

            readyLatch.countDown();

            try {
                startLatch.await();

                applicationService.accept(
                        applicationId2,
                        clientId
                );

            } catch (Exception e) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " 승인 실패: "
                                + e.getClass().getSimpleName()
                                + " / "
                                + e.getMessage()
                );
            } finally {
                doneLatch.countDown();
            }
        });

        // 두 스레드 준비
        readyLatch.await();

        // 동시에 승인 시작
        startLatch.countDown();

        // 둘 다 종료될 때까지 기다림
        doneLatch.await();

        // 최종 DB 상태 확인
        Post resultPost = postRepository.findById(postId)
                .orElseThrow();

        long acceptedCount =
                applicationRepository
                        .findAllByPostAndStatus(
                                resultPost,
                                ApplicationStatus.ACCEPTED
                        )
                        .size();

        System.out.println(
                "최종 승인된 지원자 수 = " + acceptedCount
        );

        assertEquals(1, acceptedCount);

        executorService.shutdown();
    }

    @Test
    @DisplayName("같은 지원에 승인과 취소가 동시에 요청")
    void concurrentAcceptAndCancel() throws InterruptedException {

        // 테스트용 지원 1건 생성
        User escort = userRepository.findById(escortId)
                .orElseThrow();

        Post post = postRepository.findById(postId)
                .orElseThrow();

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        application = applicationRepository.save(application);

        Long applicationId = application.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(2);

        // 승인 요청
        executorService.submit(() -> {

            readyLatch.countDown();

            try {
                startLatch.await();

                applicationService.accept(
                        applicationId,
                        clientId
                );

            } catch (Exception e) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " 승인 실패: "
                                + e.getClass().getSimpleName()
                                + " / "
                                + e.getMessage()
                );
            } finally {
                doneLatch.countDown();
            }
        });

        // 취소 요청
        executorService.submit(() -> {

            readyLatch.countDown();

            try {
                startLatch.await();

                applicationService.cancel(
                        applicationId,
                        escortId
                );

            } catch (Exception e) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " 취소 실패: "
                                + e.getClass().getSimpleName()
                                + " / "
                                + e.getMessage()
                );
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await();

        startLatch.countDown();

        doneLatch.await();

        Application resultApplication =
                applicationRepository.findById(applicationId)
                        .orElseThrow();

        Post resultPost =
                postRepository.findById(postId)
                        .orElseThrow();

        System.out.println(
                "최종 지원 상태 = "
                        + resultApplication.getStatus()
        );

        System.out.println(
                "최종 공고 상태 = "
                        + resultPost.getPostStatus()
        );

        /*
         * 정상적으로 가능한 조합인지 확인
         *
         * 1. 취소가 먼저 처리됨
         *    Application = CANCELED
         *    Post = OPEN
         *
         * 2. 승인이 먼저 처리되고 이후 취소됨
         *    Application = NO_SHOW
         *    Post = OPEN
         *
         * 3. 승인이 완료되고 취소 요청이 실패
         *    Application = ACCEPTED
         *    Post = MATCHED
         */

        boolean validState =
                (resultApplication.getStatus() == ApplicationStatus.CANCELED
                        && resultPost.getPostStatus() == PostStatus.OPEN)

                        || (resultApplication.getStatus() == ApplicationStatus.NO_SHOW
                        && resultPost.getPostStatus() == PostStatus.OPEN)

                        || (resultApplication.getStatus() == ApplicationStatus.ACCEPTED
                        && resultPost.getPostStatus() == PostStatus.MATCHED);

        assertTrue(
                validState,
                "지원 상태와 공고 상태가 일치하지 않습니다."
        );

        executorService.shutdown();
    }

    @Test
    @DisplayName("같은 진행 상태 변경 요청이 동시에 들어오면 중복 로그가 생성되지 않아야 한다")
    void concurrentProgressUpdate() throws InterruptedException {

        User escort = userRepository.findById(escortId)
                .orElseThrow();

        Post post = postRepository.findById(postId)
                .orElseThrow();

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        application.accept();
        application = applicationRepository.save(application);

        // 지원이 승인되면 공고도 매칭 완료 상태여야 출발(동행 진행 중) 처리를 할 수 있다
        post.match();
        postRepository.save(post);

        Long applicationId = application.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {

            executorService.submit(() -> {

                readyLatch.countDown();

                try {
                    startLatch.await();

                    applicationService.updateProgress(
                            applicationId,
                            escortId,
                            EscortProgress.DEPARTED
                    );

                } catch (Exception e) {
                    System.out.println(
                            Thread.currentThread().getName()
                                    + " 진행 상태 변경 실패: "
                                    + e.getClass().getSimpleName()
                                    + " / "
                                    + e.getMessage()
                    );
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        long progressLogCount =
                escortProgressLogRepository.count();

        System.out.println(
                "저장된 진행 로그 개수 = " + progressLogCount
        );

        assertEquals(1, progressLogCount);

        executorService.shutdown();
    }

    @Test
    @DisplayName("서로 다른 공고에서 같은 동행인을 동시에 승인")
    void concurrentAcceptSameEscortDifferentPosts() throws InterruptedException {

        User client = userRepository.findById(clientId).orElseThrow();
        User escort = userRepository.findById(escortId).orElseThrow();
        Post post1 = postRepository.findById(postId).orElseThrow();

        // 기존 공고와 시간이 겹치는 두 번째 공고
        Post post2 = Post.builder()
                .client(client)
                .title("동시 승인 테스트 공고 B")
                .content("동일 동행인 중복 승인 테스트")
                .region("수원")
                .hospitalName("테스트 병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now())
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(post1.getEscortStartAt())
                .escortEndAt(post1.getEscortEndAt())
                .patientNote("테스트")
                .reportRequired(false)
                .build();

        post2 = postRepository.save(post2);

        // 두 번째 공고도 결제 완료 상태로 설정
        saveDonePayment(post2);

        Application application1 = applicationRepository.save(
                Application.builder()
                        .post(post1)
                        .escort(escort)
                        .build());

        Application application2 = applicationRepository.save(
                Application.builder()
                        .post(post2)
                        .escort(escort)
                        .build());

        Long applicationId1 = application1.getId();
        Long applicationId2 = application2.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (Long applicationId : new Long[]{applicationId1, applicationId2}) {
            executor.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    applicationService.accept(applicationId, clientId);
                    successCount.incrementAndGet();

                } catch (DuplicatedException e) {
                    failureCount.incrementAndGet();
                    conflictCount.incrementAndGet();

                    System.out.println("정상 충돌: " + e.getMessage());

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    unexpectedCount.incrementAndGet();

                    System.out.println(
                            "예상하지 못한 오류: "
                                    + e.getClass().getSimpleName()
                                    + " / " + e.getMessage()
                    );
                } finally {
                        doneLatch.countDown();
                    }
                });
        }

        try {
            readyLatch.await();
            startLatch.countDown();

            assertTrue(
                    doneLatch.await(20, java.util.concurrent.TimeUnit.SECONDS),
                    "승인 요청이 제한 시간 내에 종료되지 않았습니다."
            );

            long acceptedCount = applicationRepository
                    .findAllByEscortAndStatus(
                            escort,
                            ApplicationStatus.ACCEPTED
                    ).size();

            System.out.println("승인 성공 수 = " + successCount.get());
            System.out.println("승인 실패 수 = " + failureCount.get());
            System.out.println("최종 승인 수 = " + acceptedCount);

            assertEquals(1, successCount.get(), "승인은 한 건만 성공해야 합니다.");
            assertEquals(1, failureCount.get(), "한 건은 충돌 처리되어야 합니다.");
            assertEquals(1, acceptedCount, "최종 승인된 지원은 한 건이어야 합니다.");
            assertEquals(1, conflictCount.get(), "정상적인 비즈니스 충돌이 발생해야 합니다.");
            assertEquals(0, unexpectedCount.get(), "DB 교착상태 등 예상하지 못한 오류가 없어야 합니다.");

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("지원 승인과 동행매니저 영구 정지가 동시에 발생해도 승인 상태가 남지 않는다")
    void concurrentAcceptAndBan() throws InterruptedException {

        // given: 승인 대기 중인 지원 생성
        User escort = userRepository.findById(escortId)
                .orElseThrow();

        Post post = postRepository.findById(postId)
                .orElseThrow();

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        Long applicationId = applicationRepository.saveAndFlush(application).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> acceptError =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Throwable> suspendError =
                new java.util.concurrent.atomic.AtomicReference<>();

        try {
            // 요청 1: 의뢰인이 지원 승인
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.accept(applicationId, clientId);
                } catch (Throwable e) {
                    acceptError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            // 요청 2: 관리자가 동행매니저 일시 정지
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    // 테스트에서는 관리자 ID 대신 별도의 기존 ID 사용
                    // 현재 suspendUser의 adminId는 정지 이력 기록에 사용
                    userService.banUser(
                            clientId,
                            escortId,
                            "영구 정지 동시성 테스트"
                    );
                } catch (Throwable e) {
                    suspendError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과");

            startLatch.countDown();

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.");

            // then: 최종 DB 상태 확인
            User resultEscort = userRepository.findById(escortId)
                    .orElseThrow();

            Application resultApplication = applicationRepository
                    .findById(applicationId)
                    .orElseThrow();

            Post resultPost = postRepository.findById(postId)
                    .orElseThrow();

            System.out.println("승인 예외 = " + acceptError.get());
            System.out.println("정지 예외 = " + suspendError.get());
            System.out.println("회원 상태 = " + resultEscort.getUserStatus());
            System.out.println("지원 상태 = " + resultApplication.getStatus());
            System.out.println("공고 상태 = " + resultPost.getPostStatus());

            // 정상적인 충돌 예외 외의 DB 오류는 허용하지 않음
            Throwable error = acceptError.get();
            assertTrue(error == null
                            || error instanceof ForbiddenException
                            || error instanceof InvalidException,
                    "승인 중 예상치 못한 예외: " + error);

            assertEquals(null, suspendError.get(),
                    "회원 정지 처리 중 예외가 발생했습니다.");

            assertEquals(UserStatus.BANNED,
                    resultEscort.getUserStatus());

            assertEquals(ApplicationStatus.CANCELED,
                    resultApplication.getStatus(),
                    "영구 정지된 동행매니저의 지원은 취소되어야 합니다.");

            assertTrue(
                    resultPost.getPostStatus() == PostStatus.OPEN
                            || resultPost.getPostStatus() == PostStatus.CANCELED,
                    "영구 정지 후 공고 상태는 OPEN 또는 CANCELED여야 합니다. 실제: "
                            + resultPost.getPostStatus()
            );

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("지원 승인과 동행매니저 탈퇴가 동시에 발생해도 상태가 일치한다")
    void concurrentAcceptAndWithdraw() throws InterruptedException {

        User escort = userRepository.findById(escortId).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        Long applicationId =
                applicationRepository.saveAndFlush(application).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> acceptError =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Throwable> withdrawError =
                new java.util.concurrent.atomic.AtomicReference<>();

        try {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.accept(applicationId, clientId);
                } catch (Throwable e) {
                    acceptError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.deleteMyProfile(escortId);
                } catch (Throwable e) {
                    withdrawError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과");

            startLatch.countDown();

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.");

            User resultEscort = userRepository
                    .findByIdIncludingDeleted(escortId)
                    .orElseThrow();

            Application resultApplication = applicationRepository
                    .findById(applicationId)
                    .orElseThrow();

            Post resultPost = postRepository
                    .findById(postId)
                    .orElseThrow();

            System.out.println("승인 예외 = " + acceptError.get());
            System.out.println("탈퇴 예외 = " + withdrawError.get());
            System.out.println("회원 삭제 여부 = " + resultEscort.isDeleted());
            System.out.println("지원 상태 = " + resultApplication.getStatus());
            System.out.println("공고 상태 = " + resultPost.getPostStatus());

            // 탈퇴가 정상 완료된 경우에는 활성 매칭이 남아서는 안 된다.
            if (withdrawError.get() == null) {

                assertTrue(resultEscort.isDeleted(),
                        "탈퇴가 성공했는데 회원이 삭제 상태가 아닙니다.");

                assertEquals(ApplicationStatus.CANCELED,
                        resultApplication.getStatus(),
                        "탈퇴한 동행매니저의 지원이 취소되지 않았습니다.");

                assertTrue(
                        resultPost.getPostStatus() == PostStatus.OPEN
                                || resultPost.getPostStatus() == PostStatus.CANCELED,
                        "탈퇴 후 공고가 MATCHED 상태로 남았습니다."
                );
            }

            // 탈퇴가 실패한 경우에는 오류를 출력하여 원인을 확인한다.
            // 아직 모든 탈퇴 실패 원인을 정상으로 인정하지는 않는다.
            assertTrue(withdrawError.get() == null,
                    "탈퇴 처리 중 오류 발생: " + withdrawError.get());

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("지원 승인과 공고 취소가 동시에 발생해도 상태가 일치한다")
    void concurrentAcceptAndPostCancel() throws InterruptedException {

        User escort = userRepository.findById(escortId).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();

        Application application = Application.builder()
                .post(post)
                .escort(escort)
                .build();

        Long applicationId =
                applicationRepository.saveAndFlush(application).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> acceptError =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Throwable> cancelError =
                new java.util.concurrent.atomic.AtomicReference<>();

        try {
            // 요청 1: 의뢰인이 지원 승인
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.accept(applicationId, clientId);
                } catch (Throwable e) {
                    acceptError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            // 요청 2: 의뢰인이 공고 취소
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    postService.cancel(postId, clientId);
                } catch (Throwable e) {
                    cancelError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과");

            startLatch.countDown();

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.");

            Application resultApplication = applicationRepository
                    .findById(applicationId)
                    .orElseThrow();

            Post resultPost = postRepository
                    .findById(postId)
                    .orElseThrow();

            System.out.println("승인 예외 = " + acceptError.get());
            System.out.println("취소 예외 = " + cancelError.get());
            System.out.println("지원 상태 = " + resultApplication.getStatus());
            System.out.println("공고 상태 = " + resultPost.getPostStatus());

            assertEquals(null, cancelError.get(),
                    "공고 취소 처리 중 예외가 발생했습니다.");

            assertEquals(PostStatus.CANCELED,
                    resultPost.getPostStatus(),
                    "취소 요청 후 공고가 취소되지 않았습니다.");

            assertEquals(ApplicationStatus.CANCELED,
                    resultApplication.getStatus(),
                    "공고가 취소됐는데 지원이 취소되지 않았습니다.");

            Throwable error = acceptError.get();

            assertTrue(error == null
                            || error instanceof InvalidException
                            || error instanceof ForbiddenException,
                    "승인 중 예상하지 못한 예외: " + error);

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("매칭 취소와 다른 지원자 승인 시도가 동시에 발생해도 상태가 일치한다")
    void concurrentMatchedCancelAndAccept() throws InterruptedException {

        User client = userRepository.findById(clientId).orElseThrow();
        User escortA = userRepository.findById(escortId).orElseThrow();
        Post post = postRepository.findById(postId).orElseThrow();

        int seq = TEST_SEQUENCE.incrementAndGet();

        User escortB = userRepository.save(new User(
                "matchedCancelEscort" + seq,
                passwordEncoder.encode("testPassword"),
                "matchedCancelEscort" + seq + "@test.com",
                "동행인B",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                String.format("010-4444-%04d", seq),
                "수원"
        ));

        saveVerifiedProfile(escortB, "5678");

        Application appA = Application.builder()
                .post(post)
                .escort(escortA)
                .build();

        Application appB = Application.builder()
                .post(post)
                .escort(escortB)
                .build();

        appA.accept();
        appB.reject();
        post.match();

        Long appAId = applicationRepository.saveAndFlush(appA).getId();
        Long appBId = applicationRepository.saveAndFlush(appB).getId();

        postRepository.saveAndFlush(post);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> acceptError =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Throwable> cancelError =
                new java.util.concurrent.atomic.AtomicReference<>();

        try {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.accept(appBId, clientId);
                } catch (Throwable e) {
                    acceptError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    postService.matchedCancel(postId, clientId);
                } catch (Throwable e) {
                    cancelError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과");

            startLatch.countDown();

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.");

            Application resultA = applicationRepository
                    .findById(appAId).orElseThrow();

            Application resultB = applicationRepository
                    .findById(appBId).orElseThrow();

            Post resultPost = postRepository
                    .findById(postId).orElseThrow();

            System.out.println("승인 예외 = " + acceptError.get());
            System.out.println("매칭 취소 예외 = " + cancelError.get());
            System.out.println("A 지원 상태 = " + resultA.getStatus());
            System.out.println("B 지원 상태 = " + resultB.getStatus());
            System.out.println("공고 상태 = " + resultPost.getPostStatus());

            assertEquals(null, cancelError.get(),
                    "매칭 취소 요청은 성공해야 합니다.");

            assertTrue(acceptError.get() instanceof DuplicatedException,
                    "거절된 지원자의 재승인은 충돌 예외로 막혀야 합니다.");

            assertEquals(ApplicationStatus.CANCELED,
                    resultA.getStatus());

            assertEquals(ApplicationStatus.REJECTED,
                    resultB.getStatus());

            assertEquals(PostStatus.CANCELED,
                    resultPost.getPostStatus());

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("서로 다른 공고에서 지원 승인과 지원 취소가 동시에 발생해도 상태가 일치한다")
    void concurrentAcceptAndCancelDifferentPosts() throws InterruptedException {

        User client = userRepository.findById(clientId).orElseThrow();
        User escort = userRepository.findById(escortId).orElseThrow();
        Post postA = postRepository.findById(postId).orElseThrow();

        // 공고 A와 동행 시간이 겹치는 공고 B 생성
        Post postB = Post.builder()
                .client(client)
                .title("동시 지원 취소 테스트 공고 B")
                .content("서로 다른 공고 승인 및 취소 동시성 테스트")
                .region("수원")
                .hospitalName("테스트 병원")
                .hospitalAddress("경기도 수원시")
                .hospitalLat(BigDecimal.valueOf(37.2795))
                .hospitalLng(BigDecimal.valueOf(127.0476))
                .pickupAddress("경기도 수원시")
                .pickupLat(BigDecimal.valueOf(37.2636))
                .pickupLng(BigDecimal.valueOf(127.0286))
                .hourlyPay(15000)
                .recruitStartAt(LocalDateTime.now())
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(postA.getEscortStartAt())
                .escortEndAt(postA.getEscortEndAt())
                .patientNote("테스트")
                .reportRequired(false)
                .build();

        postB = postRepository.saveAndFlush(postB);
        Long postBId = postB.getId();

        // 공고 B 결제 완료
        saveDonePayment(postB);

        Application appA = Application.builder()
                .post(postA)
                .escort(escort)
                .build();

        Application appB = Application.builder()
                .post(postB)
                .escort(escort)
                .build();

        Long appAId = applicationRepository.saveAndFlush(appA).getId();
        Long appBId = applicationRepository.saveAndFlush(appB).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<Throwable> acceptError =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<Throwable> cancelError =
                new java.util.concurrent.atomic.AtomicReference<>();

        try {
            // 요청 1: 공고 A의 지원 승인
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.accept(appAId, clientId);
                } catch (Throwable e) {
                    acceptError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            // 요청 2: 공고 B의 지원 취소
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    applicationService.cancel(appBId, escortId);
                } catch (Throwable e) {
                    cancelError.set(e);
                } finally {
                    doneLatch.countDown();
                }
            });

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과");

            startLatch.countDown();

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.");

            Application resultA = applicationRepository
                    .findById(appAId).orElseThrow();

            Application resultB = applicationRepository
                    .findById(appBId).orElseThrow();

            Post resultPostA = postRepository
                    .findById(postId).orElseThrow();

            Post resultPostB = postRepository
                    .findById(postBId).orElseThrow();

            System.out.println("승인 예외 = " + acceptError.get());
            System.out.println("취소 예외 = " + cancelError.get());
            System.out.println("A 지원 상태 = " + resultA.getStatus());
            System.out.println("B 지원 상태 = " + resultB.getStatus());
            System.out.println("A 공고 상태 = " + resultPostA.getPostStatus());
            System.out.println("B 공고 상태 = " + resultPostB.getPostStatus());

            // 공고 A의 승인은 정상적으로 완료되어야 함
            assertEquals(null, acceptError.get(),
                    "공고 A 승인 중 예외가 발생했습니다.");

            assertEquals(ApplicationStatus.ACCEPTED,
                    resultA.getStatus());

            assertEquals(PostStatus.MATCHED,
                    resultPostA.getPostStatus());

            // B 지원은 취소되거나 중복 일정으로 거절되어야 함
            assertTrue(
                    resultB.getStatus() == ApplicationStatus.CANCELED
                            || resultB.getStatus() == ApplicationStatus.REJECTED,
                    "공고 B 지원 상태가 올바르지 않습니다. 실제: "
                            + resultB.getStatus()
            );

            assertEquals(PostStatus.OPEN,
                    resultPostB.getPostStatus());

            // 취소 성공이라면 CANCELED여야 하고,
            // 거절된 경우라면 정상적인 상태 전이 오류만 허용
            if (cancelError.get() == null) {
                assertEquals(ApplicationStatus.CANCELED,
                        resultB.getStatus());
            } else {
                assertTrue(cancelError.get() instanceof InvalidException,
                        "지원 취소 중 예상하지 못한 예외: "
                                + cancelError.get());

                assertEquals(ApplicationStatus.REJECTED,
                        resultB.getStatus());
            }

        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }
}
