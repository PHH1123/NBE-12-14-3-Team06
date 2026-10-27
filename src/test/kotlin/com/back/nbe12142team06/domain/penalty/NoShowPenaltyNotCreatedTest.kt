package com.back.nbe12142team06.domain.penalty

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 패널티 없이 취소되는 경로들이 NoShowPenalty 행을 만들지 않는지 확인한다.
 * 노쇼 횟수만 보는 테스트(ApplicationCancelAllServiceTest)와 달리 패널티 테이블을 직접 센다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoShowPenaltyNotCreatedTest {

    @field:Autowired private lateinit var applicationService: ApplicationService
    @field:Autowired private lateinit var postService: PostService
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var noShowPenaltyRepository: NoShowPenaltyRepository

    // 공고 취소 시 환불은 호출만 확인하고 외부 API 는 타지 않는다
    @field:MockitoBean private lateinit var paymentService: PaymentService

    private lateinit var client: User
    private lateinit var escort: User

    @BeforeEach
    fun setUp() {
        client = userRepository.save(createUser("penaltyFreeClient", Role.CLIENT, "010-6666-0001"))
        escort = userRepository.save(createUser("penaltyFreeEscort", Role.ESCORT, "010-6666-0002"))
        escortProfileRepository.save(
            EscortProfile(escort, "동행 매니저입니다.", "오픈은행", "동행매니저", "123-000000-123")
        )
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User = User(
        username, "password1!", "$username@test.test",
        if (role == Role.ESCORT) "동행매니저" else "의뢰인",
        role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
    )

    private fun savePost(status: PostStatus): Post = postRepository.save(
        Post(
            title = "정형외과 동행 구합니다",
            content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
            region = "서울",
            hospitalName = "서울성모병원",
            hospitalAddress = "서울 서초구 반포대로 222",
            hospitalLat = BigDecimal.valueOf(37.5012743),
            hospitalLng = BigDecimal.valueOf(127.0051893),
            pickupAddress = "서울 서초구 잠원동 10-1",
            pickupLat = BigDecimal.valueOf(37.5160000),
            pickupLng = BigDecimal.valueOf(127.0200000),
            hourlyPay = 15_000,
            recruitStartAt = LocalDateTime.now().minusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(3),
            escortStartAt = LocalDateTime.now().plusDays(5),
            escortEndAt = LocalDateTime.now().plusDays(5).plusHours(4),
            postStatus = status,
            client = client,
        )
    )

    private fun saveApplication(post: Post, status: ApplicationStatus): Application {
        val application = Application(post, escort)
        if (status == ApplicationStatus.ACCEPTED) {
            application.accept()
        }
        return applicationRepository.save(application)
    }

    @Test
    @DisplayName("[NoShowPenalty] 대기 중인 지원을 본인이 취소하면 패널티가 생기지 않는다")
    fun pendingCancelCreatesNoPenalty() {
        val application = saveApplication(savePost(PostStatus.OPEN), ApplicationStatus.PENDING)

        applicationService.cancel(application.requiredId, escort.id)

        assertThat(applicationRepository.findByIdOrNull(application.requiredId)!!.status)
            .isEqualTo(ApplicationStatus.CANCELED)
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)).isEmpty()
        assertThat(escortProfileRepository.findByIdOrNull(escort.id)!!.noShowCount).isEqualTo(0)
    }

    @Test
    @DisplayName("[NoShowPenalty] 회원 탈퇴·정지에 의한 일괄 취소는 패널티를 만들지 않는다")
    fun cancelAllByEscortCreatesNoPenalty() {
        val matchedPost = savePost(PostStatus.MATCHED)
        val accepted = saveApplication(matchedPost, ApplicationStatus.ACCEPTED)
        val pending = saveApplication(savePost(PostStatus.OPEN), ApplicationStatus.PENDING)

        applicationService.cancelAllByEscort(escort.id)

        assertThat(applicationRepository.findByIdOrNull(accepted.requiredId)!!.status)
            .isEqualTo(ApplicationStatus.CANCELED)
        assertThat(applicationRepository.findByIdOrNull(pending.requiredId)!!.status)
            .isEqualTo(ApplicationStatus.CANCELED)

        // 승인된 지원을 취소했어도 탈퇴·정지 경로에는 패널티가 없다
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)).isEmpty()
        assertThat(escortProfileRepository.findByIdOrNull(escort.id)!!.noShowCount).isEqualTo(0)
    }

    @Test
    @DisplayName("[NoShowPenalty] 의뢰인이 공고를 취소하면 동행인에게 패널티가 생기지 않는다")
    fun postCancelCreatesNoPenalty() {
        val post = savePost(PostStatus.MATCHED)
        val accepted = saveApplication(post, ApplicationStatus.ACCEPTED)

        postService.cancel(post.requiredId, client.id)

        assertThat(applicationRepository.findByIdOrNull(accepted.requiredId)!!.status)
            .isEqualTo(ApplicationStatus.CANCELED)
        assertThat(postRepository.findByIdOrNull(post.requiredId)!!.postStatus)
            .isEqualTo(PostStatus.CANCELED)
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)).isEmpty()
        assertThat(escortProfileRepository.findByIdOrNull(escort.id)!!.noShowCount).isEqualTo(0)
    }

    @Test
    @DisplayName("[NoShowPenalty] 의뢰인이 매칭을 취소하면 동행인에게 패널티가 생기지 않는다")
    fun matchedCancelCreatesNoPenalty() {
        val post = savePost(PostStatus.MATCHED)
        val accepted = saveApplication(post, ApplicationStatus.ACCEPTED)

        postService.matchedCancel(post.requiredId, client.id)

        assertThat(applicationRepository.findByIdOrNull(accepted.requiredId)!!.status)
            .isEqualTo(ApplicationStatus.CANCELED)
        assertThat(noShowPenaltyRepository.findByEscortIdAndStatus(escort.id)).isEmpty()
        assertThat(escortProfileRepository.findByIdOrNull(escort.id)!!.noShowCount).isEqualTo(0)
    }
}
