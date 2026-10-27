package com.back.nbe12142team06.integration

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.ride.entity.RideSelect
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
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 공고 취소 / 매칭 취소 시 그 공고의 지원이 함께 정리되는지 확인하는 통합 테스트.
 * 테스트마다 트랜잭션이 롤백되어 데이터가 남지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostCancelApplicationIntegrationTest {

    @field:Autowired
    private lateinit var userService: UserService

    @field:Autowired
    private lateinit var postService: PostService

    @field:Autowired
    private lateinit var applicationService: ApplicationService

    @field:Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @field:Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @field:Autowired
    private lateinit var paymentRepository: PaymentRepository

    // 외부 결제 API 는 호출하지 않는다
    @field:MockitoBean
    private lateinit var tossPaymentClient: TossPaymentClient

    private lateinit var client: User
    private lateinit var escortA: User
    private lateinit var escortB: User

    @BeforeEach
    fun setUp() {
        client = signUp("cancelClient", "의뢰인", Role.CLIENT, "010-5552-0001")
        escortA = signUpEscort("cancelEscortA", "동행인A", "010-5552-0002")
        escortB = signUpEscort("cancelEscortB", "동행인B", "010-5552-0003")
    }

    private fun signUp(username: String, name: String, role: Role, phone: String): User =
        userService.signUp(
            UserSignUpRequest(
                username, "testPassword1!", "$username@test.test", name, role,
                Gender.MALE, LocalDate.of(1990, 5, 20), phone, "서울시"
            )
        )

    // 동행 매니저는 프로필을 만들고 교육 이수(verify)까지 해야 지원할 수 있다.
    private fun signUpEscort(username: String, name: String, phone: String): User {
        val escort = signUp(username, name, Role.ESCORT, phone)
        escortProfileRepository.save(EscortProfile(escort, "소개", "은행", name, "000-1234567-000"))
        escortProfileRepository.verify(escort.id, LocalDateTime.now())
        return escort
    }

    // 공고를 작성하고 결제 완료 상태로 만든다. daysLater: 동행 날짜 (같은 값이면 시간이 겹친다)
    private fun newPost(daysLater: Int): Post {
        val request = PostWriteRequest(
            "정형외과 동행 구합니다", "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.", "서울",
            "서울성모병원", "서울 서초구 반포대로 222",
            BigDecimal.valueOf(37.5012743), BigDecimal.valueOf(127.0051893),
            "서울 서초구 잠원동 10-1", BigDecimal.valueOf(37.5160000), BigDecimal.valueOf(127.0200000),
            15_000,
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays((daysLater - 1).toLong()),
            LocalDateTime.now().plusDays(daysLater.toLong()), LocalDateTime.now().plusDays(daysLater.toLong()).plusHours(4),
            RideSelect.TAXI, RideSelect.TAXI, "", true
        )

        var post = postService.findById(postService.write(client.id, request).id)
        paymentRepository.testStatusDoneAndPutPaymentKey(post.requiredId, UUID.randomUUID().toString())
        // 공고 다시 조회
        post = postService.findById(post.requiredId)
        ReflectionTestUtils.setField(post, "recruitStartAt", LocalDateTime.now().minusMinutes(1))
        return post
    }

    private fun apply(post: Post, escort: User): Long =
        applicationService.apply(post.requiredId, escort.id).id

    private fun statusOf(applicationId: Long): ApplicationStatus =
        applicationRepository.findByIdOrNull(applicationId)!!.status

    @Test
    @DisplayName("[통합] 모집 중인 공고를 취소하면 그 공고의 대기 지원은 모두 취소되고, 다른 공고의 지원은 그대로다")
    fun 공고를_취소하면_대기_지원이_취소된다() {
        val post = newPost(7)
        val otherPost = newPost(20)
        val applicationA = apply(post, escortA)
        val applicationB = apply(post, escortB)
        val otherApplication = apply(otherPost, escortA) // 다른 공고에 한 지원

        postService.cancel(post.requiredId, client.id)

        assertThat(postService.findById(post.requiredId).postStatus).isEqualTo(PostStatus.CANCELED)
        assertThat(statusOf(applicationA)).isEqualTo(ApplicationStatus.CANCELED)
        assertThat(statusOf(applicationB)).isEqualTo(ApplicationStatus.CANCELED)
        assertThat(statusOf(otherApplication)).isEqualTo(ApplicationStatus.PENDING) // 다른 공고는 영향 없음
    }

    @Test
    @DisplayName("[통합] 매칭 취소하면 승인된 지원이 패널티 없이 취소되고, 그 동행인이 겹치는 시간의 다른 공고에 다시 매칭될 수 있다")
    fun 매칭을_취소하면_승인_지원이_패널티없이_취소된다() {
        val post = newPost(7)
        val application = apply(post, escortA)
        applicationService.accept(application, client.id) // 공고가 MATCHED, 지원은 ACCEPTED

        postService.matchedCancel(post.requiredId, client.id)

        assertThat(statusOf(application)).isEqualTo(ApplicationStatus.CANCELED)
        // 패널티 없음: 노쇼 횟수가 늘지 않는다
        assertThat(escortProfileRepository.findByIdOrNull(escortA.id)!!.noShowCount).isZero()

        // newPost()의 testStatusDone()은 clearAutomatically=true로 영속성 컨텍스트를 비운다.
        // 먼저 매칭 취소 상태를 DB에 반영해 테스트 도중 변경 사항이 사라지지 않게 한다.
        applicationRepository.flush()

        // 취소된 지원이 승인 상태로 남아 있으면 같은 시간대의 다른 공고 승인이 "시간 겹침"으로 막힌다.
        val sameTimePost = newPost(7)
        val newApplication = apply(sameTimePost, escortA)
        applicationService.accept(newApplication, client.id) // 예외 없이 승인되어야 한다
        assertThat(statusOf(newApplication)).isEqualTo(ApplicationStatus.ACCEPTED)
    }
}
