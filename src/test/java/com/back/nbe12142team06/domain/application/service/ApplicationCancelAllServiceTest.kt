package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
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
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class ApplicationCancelAllServiceTest {

    @field:Autowired private lateinit var applicationService: ApplicationService
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Autowired private lateinit var passwordEncoder: PasswordEncoder
    @field:MockitoBean private lateinit var paymentService: PaymentService

    companion object {
        private val SEQ = AtomicInteger()
    }

    private lateinit var client: User
    private lateinit var escort: User

    @BeforeEach
    fun setUp() {
        val seq = SEQ.incrementAndGet()
        client = saveUser("testClient$seq", Role.CLIENT, String.format("010-7001-%04d", seq))
        escort = saveUser("testEscort$seq", Role.ESCORT, String.format("010-7002-%04d", seq))
    }

    private fun saveUser(username: String, role: Role, phone: String): User {
        return userRepository.save(User(
            username,
            passwordEncoder.encode("testPassword"),
            "$username@test.com",
            "테스트",
            role,
            Gender.MALE,
            LocalDate.of(1990, 1, 1),
            phone,
            "서울"
        ))
    }

    private fun newPost(owner: User): Post {
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
            .build())
    }

    @Test
    @DisplayName("PENDING 지원은 패널티 없이 취소되고 공고는 OPEN으로 유지된다")
    fun 대기중인_지원은_패널티없이_취소된다() {
        // given: 모집 중 공고와 대기 중인 지원 생성
        val post = newPost(client)
        val application = applicationRepository.save(Application.builder().post(post).escort(escort).build())

        // when: 회원 탈퇴·정지용 일괄 지원취소 실행
        applicationService.cancelAllByEscort(escort.id)

        // then: 지원은 취소되고 공고는 그대로 모집 중
        val result = applicationRepository.findById(application.id!!).orElseThrow()
        val resultPost = postRepository.findById(post.id!!).orElseThrow()
        assertThat(result.status).isEqualTo(ApplicationStatus.CANCELED)
        assertThat(result.activePostId).isNull()
        assertThat(resultPost.postStatus).isEqualTo(PostStatus.OPEN)

        // PENDING 취소에서는 환불이 호출되지 않아야 함
        verifyNoInteractions(paymentService)
    }

    @Test
    @DisplayName("ACCEPTED 지원은 패널티 없이 취소되고 MATCHED 공고도 취소된다")
    fun 승인된_지원은_패널티없이_취소된다() {
        // given: 지원이 승인되어 공고가 매칭된 상황
        val post = newPost(client)
        post.match()
        postRepository.save(post)

        var application = Application.builder().post(post).escort(escort).build()
        application.accept()
        application = applicationRepository.save(application)

        // when: 탈퇴·정지로 인한 일괄 지원취소
        applicationService.cancelAllByEscort(escort.id)

        // then: 지원 상태 검증
        val result = applicationRepository.findById(application.id!!).orElseThrow()
        val resultPost = postRepository.findById(post.id!!).orElseThrow()
        assertThat(result.status).isEqualTo(ApplicationStatus.CANCELED)
        assertThat(result.acceptedPostId).isNull()
        assertThat(result.activePostId).isNull()
        assertThat(resultPost.postStatus).isEqualTo(PostStatus.CANCELED)

        // 환불 서비스가 정확히 한 번 호출되었는지 검증
        verify(paymentService, times(1)).cancel(argThat { p: Post -> p.id == post.id })
    }

    @Test
    @DisplayName("동행 진행 중인 지원은 일괄 취소 대상에서 제외된다")
    fun 진행중인_동행은_취소되지_않는다() {
        // given: 승인된 지원과 진행 중인 공고
        val post = newPost(client)
        post.match()
        post.startProgress(LocalDateTime.now())
        postRepository.save(post)

        var application = Application.builder().post(post).escort(escort).build()
        application.accept()
        application = applicationRepository.save(application)

        // when: 탈퇴·정지용 지원 일괄 취소
        applicationService.cancelAllByEscort(escort.id)

        // then: 기존 상태가 그대로 유지되는지 확인
        val result = applicationRepository.findById(application.id!!).orElseThrow()
        val resultPost = postRepository.findById(post.id!!).orElseThrow()
        assertThat(result.status).isEqualTo(ApplicationStatus.ACCEPTED)
        assertThat(resultPost.postStatus).isEqualTo(PostStatus.IN_PROGRESS)
        assertThat(result.acceptedPostId).isEqualTo(post.id)

        // 진행 중인 공고는 환불 요청도 없어야 함
        verifyNoInteractions(paymentService)
    }

    @Test
    @DisplayName("회원 탈퇴·정지에 의한 지원취소는 노쇼 횟수를 증가시키지 않는다")
    fun 일괄취소시_노쇼횟수는_증가하지_않는다() {
        // given: 노쇼 횟수가 이미 2회인 동행매니저
        val escortProfile = EscortProfile(escort, "테스트 동행매니저", "테스트은행", "테스트", "123-456-789")
        escortProfile.increaseNoShowCount()
        escortProfile.increaseNoShowCount()
        escortProfileRepository.save(escortProfile)

        // 승인된 지원과 매칭된 공고 생성
        val post = newPost(client)
        post.match()
        postRepository.save(post)
        val application = Application.builder().post(post).escort(escort).build()
        application.accept()
        applicationRepository.save(application)

        // when: 회원 탈퇴·정지용 패널티 없는 지원취소 실행
        applicationService.cancelAllByEscort(escort.id)

        // then: 노쇼 횟수가 2회로 유지되는지 확인
        val result = escortProfileRepository.findById(escort.id).orElseThrow()
        assertThat(result.noShowCount).isEqualTo(2)

        // 지원은 정상 취소되어야 함
        val canceledApplication = applicationRepository.findById(application.id!!).orElseThrow()
        assertThat(canceledApplication.status).isEqualTo(ApplicationStatus.CANCELED)
    }

    @Test
    @DisplayName("다른 동행매니저의 지원은 일괄 취소되지 않는다")
    fun 다른_동행매니저의_지원은_유지된다() {
        // given
        val otherEscort = saveUser("otherEscort${SEQ.incrementAndGet()}", Role.ESCORT,
            "010-8000-${String.format("%04d", SEQ.get())}")
        val myPost = newPost(client)
        val otherPost = newPost(client)
        val myApplication = applicationRepository.save(Application.builder().post(myPost).escort(escort).build())
        val otherApplication = applicationRepository.save(Application.builder().post(otherPost).escort(otherEscort).build())

        // when
        applicationService.cancelAllByEscort(escort.id)

        // then
        assertThat(applicationRepository.findById(myApplication.id!!).orElseThrow().status)
            .isEqualTo(ApplicationStatus.CANCELED)
        assertThat(applicationRepository.findById(otherApplication.id!!).orElseThrow().status)
            .isEqualTo(ApplicationStatus.PENDING)
        verifyNoInteractions(paymentService)
    }

    @Test
    @DisplayName("취소 가능한 지원이 없는 경우 정상 종료된다")
    fun 취소할_지원이_없으면_정상종료된다() {
        // when
        applicationService.cancelAllByEscort(escort.id)

        // then: 예외 없이 종료되고 환불 호출도 없어야 한다
        verifyNoInteractions(paymentService)
    }
}
