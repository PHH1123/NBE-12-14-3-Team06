package com.back.nbe12142team06.domain.penalty

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 노쇼 패널티가 다음 정산에서 차감되는지 확인하는 통합 테스트.
 * 지원 승인 후 취소로 패널티를 한 건 쌓아두고, 다른 공고를 동행 완료까지 진행시켜 정산 금액을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoShowPenaltySettlementTest {

    // 시급
    private val hourlyPay = 15_000
    // 동행 시간
    private val hours: BigDecimal = BigDecimal.valueOf(4)
    // 의뢰인 결제 금액
    private val payoutAmount: BigDecimal = hours.multiply(BigDecimal.valueOf(hourlyPay.toLong()))
    // 플랫폼 수수료
    private val platformFee: BigDecimal = payoutAmount.multiply(BigDecimal.valueOf(0.1))
    // 패널티 금액
    private val penaltyAmount: BigDecimal = payoutAmount.subtract(platformFee).multiply(BigDecimal.valueOf(0.1))
    // 동행 매니저에게 정산되는 금액
    private val settlementAmount: BigDecimal = payoutAmount.subtract(platformFee).subtract(penaltyAmount)

    @field:Autowired private lateinit var userService: UserService
    @field:Autowired private lateinit var postService: PostService
    @field:Autowired private lateinit var applicationService: ApplicationService
    @field:Autowired private lateinit var settlementRepository: SettlementRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository
    @field:Autowired private lateinit var escortProgressLogRepository: EscortProgressLogRepository
    @field:Autowired private lateinit var paymentRepository: PaymentRepository

    // 외부 결제 API 는 호출하지 않는다
    @field:MockitoBean private lateinit var tossPaymentClient: TossPaymentClient

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var post: Post
    private lateinit var application: Application

    @BeforeEach
    fun init() {
        client = signUp("testUsername", "김춘식", Role.CLIENT, "010-1234-5678")
        escort = signUpEscort("testUsername2", "김춘식2", "010-9999-9991")

        // 지원 승인 후 취소 -> 노쇼 패널티 한 건이 쌓인다
        val canceled = matchedApplication(newPost())
        applicationService.cancel(canceled.id, escort.id)

        // 패널티가 적용될 공고, 지원 후 승인까지
        post = newPost()
        application = matchedApplication(post)
    }

    private fun signUp(username: String, name: String, role: Role, phone: String): User =
        userService.signUp(
            UserSignUpRequest(
                username, "testPassword", "$username@test.test", name, role,
                Gender.MALE, LocalDate.of(1990, 5, 20), phone, "서울시"
            )
        )

    // 동행 매니저는 프로필을 만들고 교육 이수(verify)까지 해야 지원할 수 있다.
    private fun signUpEscort(username: String, name: String, phone: String): User {
        val escort = signUp(username, name, Role.ESCORT, phone)
        escortProfileRepository.save(EscortProfile(escort, "하이", "오픈은행", name, "000-1234567-000"))
        escortProfileRepository.verify(escort.id, LocalDateTime.now())
        return escort
    }

    // 공고를 작성하고 결제 완료 + 모집 시작 상태로 만든다.
    private fun newPost(): Post {
        val request = PostWriteRequest(
            "정형외과 동행 구합니다", "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.", "서울",
            "서울성모병원", "서울 서초구 반포대로 222",
            BigDecimal.valueOf(37.5012743), BigDecimal.valueOf(127.0051893),
            "서울 서초구 잠원동 10-1", BigDecimal.valueOf(37.5160000), BigDecimal.valueOf(127.0200000),
            hourlyPay,
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(6),
            LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(7).plusHours(hours.toLong()),
            RideSelect.TAXI, RideSelect.TAXI, "", true
        )

        var post = postService.findById(postService.write(client.id, request).id!!)
        paymentRepository.testStatusDoneAndPutPaymentKey(post.id!!, UUID.randomUUID().toString())
        // 영속성 컨텍스트 초기화 후 공고 다시 조회
        post = postService.findById(post.id!!)
        ReflectionTestUtils.setField(post, "recruitStartAt", LocalDateTime.now().minusMinutes(1))
        return post
    }

    // 지원 후 승인까지 진행한 지원을 돌려준다.
    private fun matchedApplication(post: Post): Application {
        val applyResponse = applicationService.apply(post.id, escort.id)
        applicationService.accept(applyResponse.id, client.id)
        return applicationRepository.findAllByPostIdWithEscort(post.id).first()
    }

    @Test
    @DisplayName("[NoShowPenaltyService] 지원 승인 후 취소 패널티")
    fun noShow() {
        // 패널티 적용 되었는지 검증
        val escortProfile = escortProfileRepository.findById(escort.id).get()
        assertThat(escortProfile.noShowCount).isEqualTo(1)

        // 동행 시작 -> 동행 완료까지
        for (progress in EscortProgress.entries) {
            // 출발 전에는 저장하지 않음
            if (progress == EscortProgress.NOT_STARTED) continue

            var occurredAt = LocalDateTime.now()
            if (progress == EscortProgress.DEPARTED) {
                // 출발 시간에는 now()를 저장
                application.post.startProgress(occurredAt)
            } else {
                // 이후부터 단계마다 +1시간씩 저장, 기본은 2이므로 -1
                occurredAt = occurredAt.plusHours((progress.ordinal - 1).toLong())
            }
            escortProgressLogRepository.save(
                EscortProgressLog(application = application, progress = progress, occurredAt = occurredAt)
            )
        }

        // 동행 완료 처리
        postService.escortComplete(post.id!!, client.id)

        val settlement = settlementRepository.findAll().first()
        val noShowPenalty = noShowPenaltyRepository.findAll().first()

        // 1. 실제 정산 금액 2. 패널티 금액 3. 플랫폼 수수료
        assertThat(settlement.payoutAmount).isEqualByComparingTo(settlementAmount)
        assertThat(settlement.penaltyAmount).isEqualByComparingTo(penaltyAmount)
        assertThat(settlement.platformFee).isEqualByComparingTo(platformFee)
        // 1. 패널티 차감 전 금액 2. 패널티 금액
        assertThat(noShowPenalty.baseAmount).isEqualByComparingTo(payoutAmount.subtract(platformFee))
        assertThat(noShowPenalty.amount).isEqualByComparingTo(penaltyAmount)
    }
}
