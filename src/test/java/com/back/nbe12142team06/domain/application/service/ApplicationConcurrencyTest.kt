package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.exception.DuplicatedException
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class ApplicationConcurrencyTest {

    @field:Autowired

    private lateinit var applicationService: ApplicationService

    @field:Autowired

    private lateinit var applicationRepository: ApplicationRepository

    @field:Autowired

    private lateinit var userRepository: UserRepository

    @field:Autowired

    private lateinit var escortProfileRepository: EscortProfileRepository

    @field:Autowired

    private lateinit var postRepository: PostRepository

    @field:Autowired

    private lateinit var passwordEncoder: PasswordEncoder

    @field:Autowired

    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    @field:Autowired

    private lateinit var paymentRepository: PaymentRepository

    @field:Autowired

    private lateinit var transactionTemplate: TransactionTemplate

    @field:Autowired

    private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository

    @field:Autowired

    private lateinit var userService: UserService

    @field:Autowired

    private lateinit var postService: PostService

    private var postId = 0L
    private var escortId = 0L
    private var clientId = 0L

    companion object {
        private val TEST_SEQUENCE = AtomicInteger()
    }

    @BeforeEach
    fun setUp() {

        // 지원을 참조하는 자식 데이터부터 삭제
        noShowPenaltyRepository.deleteAll()
        escortProgressLogRepository.deleteAll()

        // 지원 데이터 삭제
        applicationRepository.deleteAll()

        var seq = TEST_SEQUENCE.incrementAndGet()

        var client = User(
                "concurrencyClient" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyClient" + seq + "@test.com",
                "의뢰인",
                Role.CLIENT,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                String.format("010-1111-%04d", seq),
                "수원"
        )

        client = userRepository.save(client)
        clientId = client.id

        var escort = User(
                "concurrencyEscort" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyEscort" + seq + "@test.com",
                "동행인",
                Role.ESCORT,
                Gender.FEMALE,
                LocalDate.of(1995, 1, 1),
                String.format("010-2222-%04d", seq),
                "수원"
        )

        escort = userRepository.save(escort)
        escortId = escort.id

        saveVerifiedProfile(escort, "1234")

        var post = Post.builder()
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
                .recruitStartAt(LocalDateTime.now().minusMinutes(1))
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(LocalDateTime.now().plusDays(2))
                .escortEndAt(LocalDateTime.now().plusDays(2).plusHours(3))
                .patientNote("테스트")
                .reportRequired(false)
                .build()

        postId = postRepository.save(post).id!!

        saveDonePayment(post)
    }

    private fun saveVerifiedProfile(escort: User, accountNumber: String) {
        escortProfileRepository.save(
                EscortProfile(escort, "동행인 소개", "테스트은행", escort.name, accountNumber))

        transactionTemplate.executeWithoutResult { status ->
            escortProfileRepository.verify(escort.id, LocalDateTime.now())
        }
    }

    /**
     * 결제 완료 상태의 공고를 만든다.
     *
     * 승인된 지원이 정지·탈퇴·공고 취소로 되돌려질 때 결제 환불까지 함께 실행되므로,
     * 결제 키는 TossPaymentClientDev 가 실제 토스 API 를 호출하지 않는 시드 접두어를 쓴다.
     * 접두어가 없으면 환불이 404(NOT_FOUND_PAYMENT)로 실패해 정지·탈퇴 트랜잭션이 롤백된다.
     * 환불 금액은 balanceAmount 에서 가져가므로 결제 금액과 같은 값으로 채워둔다.
     */
    private fun saveDonePayment(post: Post) {
        paymentRepository.saveAndFlush(Payment.builder()
                .amount(post.totalPay)
                .hourlyPaySnapshot(post.hourlyPay)
                .hours(post.escortHours)
                .post(post)
                .paymentKey("INIT-PAYKEY-concurrency-" + post.id)
                .balanceAmount(post.totalPay)
                .paymentStatus(PaymentStatus.DONE)
                .build())
    }


    @Test
    @DisplayName("동일한 동행인이 같은 공고에 동시에 지원")
    fun concurrentApply() {

        var threadCount = 10

        var executorService = Executors.newFixedThreadPool(threadCount)

        var readyLatch = CountDownLatch(threadCount)

        var startLatch = CountDownLatch(1)

        var doneLatch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {

            executorService.submit(Runnable {

                readyLatch.countDown()

                try {
                    startLatch.await()

                    applicationService.apply(postId, escortId)

                } catch (e: Exception) {
                    System.out.println(
                            Thread.currentThread().name
                                    + " 실패: "
                                    + e.javaClass.simpleName
                                    + " / "
                                    + e.message
                    )
                } finally {
                    doneLatch.countDown()
                }
            })
        }

        // 모든 스레드가 준비될 때까지 대기
        readyLatch.await()

        // 동시에 출발
        startLatch.countDown()

        // 모든 요청 종료까지 대기
        doneLatch.await()

        var count = applicationRepository.count()

        System.out.println("저장된 지원 개수 = " + count)

        assertEquals(1, count)

        executorService.shutdown()
    }

    @Test
    @DisplayName("같은 공고의 서로 다른 지원자를 동시에 승인")
    fun concurrentAccept() {

        var seq = TEST_SEQUENCE.incrementAndGet()

        var escort2 = User(
                "concurrencyEscort2_" + seq,
                passwordEncoder.encode("testPassword"),
                "concurrencyEscort2_" + seq + "@test.com",
                "동행인2",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                String.format("010-3333-%04d", seq),
                "수원"
        )

        escort2 = userRepository.save(escort2)

        saveVerifiedProfile(escort2, "5678")

        // 기존 동행인 조회
        var escort1 = userRepository.findById(escortId)
                .orElseThrow()

        var post = postRepository.findById(postId)
                .orElseThrow()

        // 같은 공고에 서로 다른 동행인 2명이 지원한 상태 생성
        var application1 = Application.builder()
                .post(post)
                .escort(escort1)
                .build()

        var application2 = Application.builder()
                .post(post)
                .escort(escort2)
                .build()

        application1 = applicationRepository.save(application1)
        application2 = applicationRepository.save(application2)

        var applicationId1 = application1.id
        var applicationId2 = application2.id

        var executorService = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)

        var startLatch = CountDownLatch(1)

        var doneLatch = CountDownLatch(2)

        // 지원자 1 승인
        executorService.submit(Runnable {

            readyLatch.countDown()

            try {
                startLatch.await()

                applicationService.accept(
                        applicationId1,
                        clientId
                )

            } catch (e: Exception) {
                System.out.println(
                        Thread.currentThread().name
                                + " 승인 실패: "
                                + e.javaClass.simpleName
                                + " / "
                                + e.message
                )
            } finally {
                doneLatch.countDown()
            }
        })

        // 지원자 2 승인
        executorService.submit(Runnable {

            readyLatch.countDown()

            try {
                startLatch.await()

                applicationService.accept(
                        applicationId2,
                        clientId
                )

            } catch (e: Exception) {
                System.out.println(
                        Thread.currentThread().name
                                + " 승인 실패: "
                                + e.javaClass.simpleName
                                + " / "
                                + e.message
                )
            } finally {
                doneLatch.countDown()
            }
        })

        // 두 스레드 준비
        readyLatch.await()

        // 동시에 승인 시작
        startLatch.countDown()

        // 둘 다 종료될 때까지 기다림
        doneLatch.await()

        // 최종 DB 상태 확인
        var resultPost = postRepository.findById(postId)
                .orElseThrow()

        var acceptedCount = applicationRepository
                        .findAllByPostAndStatus(
                                resultPost,
                                ApplicationStatus.ACCEPTED
                        )
                        .size

        System.out.println(
                "최종 승인된 지원자 수 = " + acceptedCount
        )

        assertEquals(1, acceptedCount)

        executorService.shutdown()
    }

    @Test
    @DisplayName("같은 지원에 승인과 취소가 동시에 요청")
    fun concurrentAcceptAndCancel() {

        // 테스트용 지원 1건 생성
        var escort = userRepository.findById(escortId)
                .orElseThrow()

        var post = postRepository.findById(postId)
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application = applicationRepository.save(application)

        var applicationId = application.id

        var executorService = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)

        var startLatch = CountDownLatch(1)

        var doneLatch = CountDownLatch(2)

        // 승인 요청
        executorService.submit(Runnable {

            readyLatch.countDown()

            try {
                startLatch.await()

                applicationService.accept(
                        applicationId,
                        clientId
                )

            } catch (e: Exception) {
                System.out.println(
                        Thread.currentThread().name
                                + " 승인 실패: "
                                + e.javaClass.simpleName
                                + " / "
                                + e.message
                )
            } finally {
                doneLatch.countDown()
            }
        })

        // 취소 요청
        executorService.submit(Runnable {

            readyLatch.countDown()

            try {
                startLatch.await()

                applicationService.cancel(
                        applicationId,
                        escortId
                )

            } catch (e: Exception) {
                System.out.println(
                        Thread.currentThread().name
                                + " 취소 실패: "
                                + e.javaClass.simpleName
                                + " / "
                                + e.message
                )
            } finally {
                doneLatch.countDown()
            }
        })

        readyLatch.await()

        startLatch.countDown()

        doneLatch.await()

        var resultApplication = applicationRepository.findById(applicationId!!)
                        .orElseThrow()

        var resultPost = postRepository.findById(postId)
                        .orElseThrow()

        System.out.println(
                "최종 지원 상태 = "
                        + resultApplication.status
        )

        System.out.println(
                "최종 공고 상태 = "
                        + resultPost.postStatus
        )

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

        var validState = (resultApplication.status == ApplicationStatus.CANCELED
                        && resultPost.postStatus == PostStatus.OPEN)

                        || (resultApplication.status == ApplicationStatus.NO_SHOW
                        && resultPost.postStatus == PostStatus.OPEN)

                        || (resultApplication.status == ApplicationStatus.ACCEPTED
                        && resultPost.postStatus == PostStatus.MATCHED)

        assertTrue(
                validState,
                "지원 상태와 공고 상태가 일치하지 않습니다."
        )

        executorService.shutdown()
    }

    @Test
    @DisplayName("같은 진행 상태 변경 요청이 동시에 들어오면 중복 로그가 생성되지 않아야 한다")
    fun concurrentProgressUpdate() {

        var escort = userRepository.findById(escortId)
                .orElseThrow()

        var post = postRepository.findById(postId)
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        application.accept()
        application = applicationRepository.save(application)

        // 지원이 승인되면 공고도 매칭 완료 상태여야 출발(동행 진행 중) 처리를 할 수 있다
        post.match()
        postRepository.save(post)

        var applicationId = application.id

        var executorService = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)

        var startLatch = CountDownLatch(1)

        var doneLatch = CountDownLatch(2)

        for (i in 0 until 2) {

            executorService.submit(Runnable {

                readyLatch.countDown()

                try {
                    startLatch.await()

                    applicationService.updateProgress(
                            applicationId,
                            escortId,
                            EscortProgress.DEPARTED
                    )

                } catch (e: Exception) {
                    System.out.println(
                            Thread.currentThread().name
                                    + " 진행 상태 변경 실패: "
                                    + e.javaClass.simpleName
                                    + " / "
                                    + e.message
                    )
                } finally {
                    doneLatch.countDown()
                }
            })
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()

        var progressLogCount = escortProgressLogRepository.count()

        System.out.println(
                "저장된 진행 로그 개수 = " + progressLogCount
        )

        assertEquals(1, progressLogCount)

        executorService.shutdown()
    }

    @Test
    @DisplayName("서로 다른 공고에서 같은 동행인을 동시에 승인")
    fun concurrentAcceptSameEscortDifferentPosts() {

        var client = userRepository.findById(clientId).orElseThrow()
        var escort = userRepository.findById(escortId).orElseThrow()
        var post1 = postRepository.findById(postId).orElseThrow()

        // 기존 공고와 시간이 겹치는 두 번째 공고
        var post2 = Post.builder()
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
                .recruitStartAt(LocalDateTime.now().minusMinutes(1))
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(post1.escortStartAt)
                .escortEndAt(post1.escortEndAt)
                .patientNote("테스트")
                .reportRequired(false)
                .build()

        post2 = postRepository.save(post2)

        // 두 번째 공고도 결제 완료 상태로 설정
        saveDonePayment(post2)

        var application1 = applicationRepository.save(
                Application.builder()
                        .post(post1)
                        .escort(escort)
                        .build())

        var application2 = applicationRepository.save(
                Application.builder()
                        .post(post2)
                        .escort(escort)
                        .build())

        var applicationId1 = application1.id
        var applicationId2 = application2.id

        var executor = Executors.newFixedThreadPool(2)
        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var successCount = AtomicInteger()
        var failureCount = AtomicInteger()
        var conflictCount = AtomicInteger()
        var unexpectedCount = AtomicInteger()

        for (applicationId in arrayOf(applicationId1, applicationId2)) {
            executor.submit(Runnable {
                readyLatch.countDown()

                try {
                    startLatch.await()

                    applicationService.accept(applicationId, clientId)
                    successCount.incrementAndGet()

                } catch (e: DuplicatedException) {
                    failureCount.incrementAndGet()
                    conflictCount.incrementAndGet()

                    System.out.println("정상 충돌: " + e.message)

                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                    unexpectedCount.incrementAndGet()

                    System.out.println(
                            "예상하지 못한 오류: "
                                    + e.javaClass.simpleName
                                    + " / " + e.message
                    )
                } finally {
                        doneLatch.countDown()
                    }
                })
        }

        try {
            readyLatch.await()
            startLatch.countDown()

            assertTrue(
                    doneLatch.await(20, java.util.concurrent.TimeUnit.SECONDS),
                    "승인 요청이 제한 시간 내에 종료되지 않았습니다."
            )

            var acceptedCount = applicationRepository
                    .findAllByEscortAndStatus(
                            escort,
                            ApplicationStatus.ACCEPTED
                    ).size

            System.out.println("승인 성공 수 = " + successCount.get())
            System.out.println("승인 실패 수 = " + failureCount.get())
            System.out.println("최종 승인 수 = " + acceptedCount)

            assertEquals(1, successCount.get(), "승인은 한 건만 성공해야 합니다.")
            assertEquals(1, failureCount.get(), "한 건은 충돌 처리되어야 합니다.")
            assertEquals(1, acceptedCount, "최종 승인된 지원은 한 건이어야 합니다.")
            assertEquals(1, conflictCount.get(), "정상적인 비즈니스 충돌이 발생해야 합니다.")
            assertEquals(0, unexpectedCount.get(), "DB 교착상태 등 예상하지 못한 오류가 없어야 합니다.")

        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("지원 승인과 동행매니저 영구 정지가 동시에 발생해도 승인 상태가 남지 않는다")
    fun concurrentAcceptAndBan() {

        // given: 승인 대기 중인 지원 생성
        var escort = userRepository.findById(escortId)
                .orElseThrow()

        var post = postRepository.findById(postId)
                .orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        var applicationId = applicationRepository.saveAndFlush(application).id

        var executor = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var acceptError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        var suspendError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        try {
            // 요청 1: 의뢰인이 지원 승인
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.accept(applicationId, clientId)
                } catch (e: Throwable) {
                    acceptError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            // 요청 2: 관리자가 동행매니저 일시 정지
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()

                    // 테스트에서는 관리자 ID 대신 별도의 기존 ID 사용
                    // 현재 suspendUser의 adminId는 정지 이력 기록에 사용
                    userService.banUser(
                            clientId,
                            escortId,
                            "영구 정지 동시성 테스트"
                    )
                } catch (e: Throwable) {
                    suspendError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과")

            startLatch.countDown()

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.")

            // then: 최종 DB 상태 확인
            var resultEscort = userRepository.findById(escortId)
                    .orElseThrow()

            var resultApplication = applicationRepository
                    .findById(applicationId!!)
                    .orElseThrow()

            var resultPost = postRepository.findById(postId)
                    .orElseThrow()

            System.out.println("승인 예외 = " + acceptError.get())
            System.out.println("정지 예외 = " + suspendError.get())
            System.out.println("회원 상태 = " + resultEscort.userStatus)
            System.out.println("지원 상태 = " + resultApplication.status)
            System.out.println("공고 상태 = " + resultPost.postStatus)

            // 정상적인 충돌 예외 외의 DB 오류는 허용하지 않음
            var error = acceptError.get()
            assertTrue(error == null
                            || error is ForbiddenException
                            || error is InvalidException,
                    "승인 중 예상치 못한 예외: " + error)

            assertEquals(null, suspendError.get(),
                    "회원 정지 처리 중 예외가 발생했습니다.")

            assertEquals(UserStatus.BANNED,
                    resultEscort.userStatus)

            assertEquals(ApplicationStatus.CANCELED,
                    resultApplication.status,
                    "영구 정지된 동행매니저의 지원은 취소되어야 합니다.")

            assertTrue(
                    resultPost.postStatus == PostStatus.OPEN
                            || resultPost.postStatus == PostStatus.CANCELED,
                    "영구 정지 후 공고 상태는 OPEN 또는 CANCELED여야 합니다. 실제: "
                            + resultPost.postStatus
            )

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("지원 승인과 동행매니저 탈퇴가 동시에 발생해도 상태가 일치한다")
    fun concurrentAcceptAndWithdraw() {

        var escort = userRepository.findById(escortId).orElseThrow()
        var post = postRepository.findById(postId).orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        var applicationId = applicationRepository.saveAndFlush(application).id

        var executor = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var acceptError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        var withdrawError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        try {
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.accept(applicationId, clientId)
                } catch (e: Throwable) {
                    acceptError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    userService.deleteMyProfile(escortId)
                } catch (e: Throwable) {
                    withdrawError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과")

            startLatch.countDown()

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.")

            var resultEscort = userRepository
                    .findByIdIncludingDeleted(escortId)
                    .orElseThrow()

            var resultApplication = applicationRepository
                    .findById(applicationId!!)
                    .orElseThrow()

            var resultPost = postRepository
                    .findById(postId)
                    .orElseThrow()

            System.out.println("승인 예외 = " + acceptError.get())
            System.out.println("탈퇴 예외 = " + withdrawError.get())
            System.out.println("회원 삭제 여부 = " + resultEscort.isDeleted())
            System.out.println("지원 상태 = " + resultApplication.status)
            System.out.println("공고 상태 = " + resultPost.postStatus)

            // 탈퇴가 정상 완료된 경우에는 활성 매칭이 남아서는 안 된다.
            if (withdrawError.get() == null) {

                assertTrue(resultEscort.isDeleted(),
                        "탈퇴가 성공했는데 회원이 삭제 상태가 아닙니다.")

                assertEquals(ApplicationStatus.CANCELED,
                        resultApplication.status,
                        "탈퇴한 동행매니저의 지원이 취소되지 않았습니다.")

                assertTrue(
                        resultPost.postStatus == PostStatus.OPEN
                                || resultPost.postStatus == PostStatus.CANCELED,
                        "탈퇴 후 공고가 MATCHED 상태로 남았습니다."
                )
            }

            // 탈퇴가 실패한 경우에는 오류를 출력하여 원인을 확인한다.
            // 아직 모든 탈퇴 실패 원인을 정상으로 인정하지는 않는다.
            assertTrue(withdrawError.get() == null,
                    "탈퇴 처리 중 오류 발생: " + withdrawError.get())

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("지원 승인과 공고 취소가 동시에 발생해도 상태가 일치한다")
    fun concurrentAcceptAndPostCancel() {

        var escort = userRepository.findById(escortId).orElseThrow()
        var post = postRepository.findById(postId).orElseThrow()

        var application = Application.builder()
                .post(post)
                .escort(escort)
                .build()

        var applicationId = applicationRepository.saveAndFlush(application).id

        var executor = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var acceptError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        var cancelError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        try {
            // 요청 1: 의뢰인이 지원 승인
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.accept(applicationId, clientId)
                } catch (e: Throwable) {
                    acceptError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            // 요청 2: 의뢰인이 공고 취소
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    postService.cancel(postId, clientId)
                } catch (e: Throwable) {
                    cancelError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과")

            startLatch.countDown()

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.")

            var resultApplication = applicationRepository
                    .findById(applicationId!!)
                    .orElseThrow()

            var resultPost = postRepository
                    .findById(postId)
                    .orElseThrow()

            System.out.println("승인 예외 = " + acceptError.get())
            System.out.println("취소 예외 = " + cancelError.get())
            System.out.println("지원 상태 = " + resultApplication.status)
            System.out.println("공고 상태 = " + resultPost.postStatus)

            assertEquals(null, cancelError.get(),
                    "공고 취소 처리 중 예외가 발생했습니다.")

            assertEquals(PostStatus.CANCELED,
                    resultPost.postStatus,
                    "취소 요청 후 공고가 취소되지 않았습니다.")

            assertEquals(ApplicationStatus.CANCELED,
                    resultApplication.status,
                    "공고가 취소됐는데 지원이 취소되지 않았습니다.")

            var error = acceptError.get()

            assertTrue(error == null
                            || error is InvalidException
                            || error is ForbiddenException,
                    "승인 중 예상하지 못한 예외: " + error)

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("매칭 취소와 다른 지원자 승인 시도가 동시에 발생해도 상태가 일치한다")
    fun concurrentMatchedCancelAndAccept() {

        var client = userRepository.findById(clientId).orElseThrow()
        var escortA = userRepository.findById(escortId).orElseThrow()
        var post = postRepository.findById(postId).orElseThrow()

        var seq = TEST_SEQUENCE.incrementAndGet()

        var escortB = userRepository.save(User(
                "matchedCancelEscort" + seq,
                passwordEncoder.encode("testPassword"),
                "matchedCancelEscort" + seq + "@test.com",
                "동행인B",
                Role.ESCORT,
                Gender.MALE,
                LocalDate.of(1996, 1, 1),
                String.format("010-4444-%04d", seq),
                "수원"
        ))

        saveVerifiedProfile(escortB, "5678")

        var appA = Application.builder()
                .post(post)
                .escort(escortA)
                .build()

        var appB = Application.builder()
                .post(post)
                .escort(escortB)
                .build()

        appA.accept()
        appB.reject()
        post.match()

        var appAId = applicationRepository.saveAndFlush(appA).id
        var appBId = applicationRepository.saveAndFlush(appB).id

        postRepository.saveAndFlush(post)

        var executor = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var acceptError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        var cancelError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        try {
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.accept(appBId, clientId)
                } catch (e: Throwable) {
                    acceptError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    postService.matchedCancel(postId, clientId)
                } catch (e: Throwable) {
                    cancelError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과")

            startLatch.countDown()

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.")

            var resultA = applicationRepository
                    .findById(appAId!!).orElseThrow()

            var resultB = applicationRepository
                    .findById(appBId!!).orElseThrow()

            var resultPost = postRepository
                    .findById(postId).orElseThrow()

            System.out.println("승인 예외 = " + acceptError.get())
            System.out.println("매칭 취소 예외 = " + cancelError.get())
            System.out.println("A 지원 상태 = " + resultA.status)
            System.out.println("B 지원 상태 = " + resultB.status)
            System.out.println("공고 상태 = " + resultPost.postStatus)

            assertEquals(null, cancelError.get(),
                    "매칭 취소 요청은 성공해야 합니다.")

            assertTrue(acceptError.get() is DuplicatedException,
                    "거절된 지원자의 재승인은 충돌 예외로 막혀야 합니다.")

            assertEquals(ApplicationStatus.CANCELED,
                    resultA.status)

            assertEquals(ApplicationStatus.REJECTED,
                    resultB.status)

            assertEquals(PostStatus.CANCELED,
                    resultPost.postStatus)

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    @DisplayName("서로 다른 공고에서 지원 승인과 지원 취소가 동시에 발생해도 상태가 일치한다")
    fun concurrentAcceptAndCancelDifferentPosts() {

        var client = userRepository.findById(clientId).orElseThrow()
        var escort = userRepository.findById(escortId).orElseThrow()
        var postA = postRepository.findById(postId).orElseThrow()

        // 공고 A와 동행 시간이 겹치는 공고 B 생성
        var postB = Post.builder()
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
                .recruitStartAt(LocalDateTime.now().minusMinutes(1))
                .recruitEndAt(LocalDateTime.now().plusDays(1))
                .escortStartAt(postA.escortStartAt)
                .escortEndAt(postA.escortEndAt)
                .patientNote("테스트")
                .reportRequired(false)
                .build()

        postB = postRepository.saveAndFlush(postB)
        var postBId = postB.id

        // 공고 B 결제 완료
        saveDonePayment(postB)

        var appA = Application.builder()
                .post(postA)
                .escort(escort)
                .build()

        var appB = Application.builder()
                .post(postB)
                .escort(escort)
                .build()

        var appAId = applicationRepository.saveAndFlush(appA).id
        var appBId = applicationRepository.saveAndFlush(appB).id

        var executor = Executors.newFixedThreadPool(2)

        var readyLatch = CountDownLatch(2)
        var startLatch = CountDownLatch(1)
        var doneLatch = CountDownLatch(2)

        var acceptError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        var cancelError = java.util.concurrent.atomic.AtomicReference<Throwable>()

        try {
            // 요청 1: 공고 A의 지원 승인
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.accept(appAId, clientId)
                } catch (e: Throwable) {
                    acceptError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            // 요청 2: 공고 B의 지원 취소
            executor.submit(Runnable {
                readyLatch.countDown()
                try {
                    startLatch.await()
                    applicationService.cancel(appBId, escortId)
                } catch (e: Throwable) {
                    cancelError.set(e)
                } finally {
                    doneLatch.countDown()
                }
            })

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                    "스레드 준비 시간 초과")

            startLatch.countDown()

            assertTrue(doneLatch.await(20, TimeUnit.SECONDS),
                    "동시 요청이 제한 시간 안에 종료되지 않았습니다.")

            var resultA = applicationRepository
                    .findById(appAId!!).orElseThrow()

            var resultB = applicationRepository
                    .findById(appBId!!).orElseThrow()

            var resultPostA = postRepository
                    .findById(postId).orElseThrow()

            var resultPostB = postRepository
                    .findById(postBId!!).orElseThrow()

            System.out.println("승인 예외 = " + acceptError.get())
            System.out.println("취소 예외 = " + cancelError.get())
            System.out.println("A 지원 상태 = " + resultA.status)
            System.out.println("B 지원 상태 = " + resultB.status)
            System.out.println("A 공고 상태 = " + resultPostA.postStatus)
            System.out.println("B 공고 상태 = " + resultPostB.postStatus)

            // 공고 A의 승인은 정상적으로 완료되어야 함
            assertEquals(null, acceptError.get(),
                    "공고 A 승인 중 예외가 발생했습니다.")

            assertEquals(ApplicationStatus.ACCEPTED,
                    resultA.status)

            assertEquals(PostStatus.MATCHED,
                    resultPostA.postStatus)

            // B 지원은 취소되거나 중복 일정으로 거절되어야 함
            assertTrue(
                    resultB.status == ApplicationStatus.CANCELED
                            || resultB.status == ApplicationStatus.REJECTED,
                    "공고 B 지원 상태가 올바르지 않습니다. 실제: "
                            + resultB.status
            )

            assertEquals(PostStatus.OPEN,
                    resultPostB.postStatus)

            // 취소 성공이라면 CANCELED여야 하고,
            // 거절된 경우라면 정상적인 상태 전이 오류만 허용
            if (cancelError.get() == null) {
                assertEquals(ApplicationStatus.CANCELED,
                        resultB.status)
            } else {
                assertTrue(cancelError.get() is InvalidException,
                        "지원 취소 중 예상하지 못한 예외: "
                                + cancelError.get())

                assertEquals(ApplicationStatus.REJECTED,
                        resultB.status)
            }

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }
}
