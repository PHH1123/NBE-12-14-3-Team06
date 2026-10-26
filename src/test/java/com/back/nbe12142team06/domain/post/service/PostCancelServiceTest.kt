package com.back.nbe12142team06.domain.post.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.InvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * PostService.cancel (공고 취소: OPEN, MATCHED -> CANCELED) 테스트.
 * 회원 탈퇴·정지 시 공고를 취소하는 흐름에서 호출한다.
 * 다른 테스트가 남긴 데이터와 충돌하지 않도록 테스트마다 고유한 사용자·공고를 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PostCancelServiceTest {

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    private lateinit var client: User

    @BeforeEach
    fun setUp() {
        // @Transactional 을 걸지 않아 데이터가 커밋된 채 남으므로, 다른 비트랜잭션 테스트와 같은 관례로 비운다
        databaseCleaner.clean()

        val seq = SEQ.incrementAndGet()
        client = saveUser("cancelClient$seq", Role.CLIENT, "010-6961-%04d".format(seq))
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    private fun saveUser(username: String, role: Role, phone: String): User {
        return userRepository.save(
            User(
                username, passwordEncoder.encode("testPassword"), "$username@test.com", "테스트",
                role, Gender.MALE, LocalDate.of(1990, 1, 1), phone, "서울",
            ),
        )
    }

    private fun newPost(owner: User): Post {
        return postRepository.save(
            Post(
                client = owner,
                title = "취소 테스트 공고", content = "내용", region = "서울",
                hospitalName = "테스트 병원", hospitalAddress = "서울시",
                hospitalLat = BigDecimal.valueOf(37.5665), hospitalLng = BigDecimal.valueOf(126.9780),
                pickupAddress = "서울시",
                pickupLat = BigDecimal.valueOf(37.5700), pickupLng = BigDecimal.valueOf(126.9800),
                hourlyPay = 15000,
                recruitStartAt = LocalDateTime.now(), recruitEndAt = LocalDateTime.now().plusDays(1),
                escortStartAt = LocalDateTime.now().plusDays(2),
                escortEndAt = LocalDateTime.now().plusDays(2).plusHours(3),
                patientNote = "테스트", reportRequired = false,
            ),
        )
    }

    private fun postWithStatus(owner: User, status: PostStatus): Post {
        val post = newPost(owner)
        when (status) {
            PostStatus.OPEN -> {}
            PostStatus.MATCHED -> post.match()
            PostStatus.IN_PROGRESS -> {
                post.match()
                post.startProgress(LocalDateTime.now())
            }
            PostStatus.COMPLETED -> {
                post.match()
                post.startProgress(LocalDateTime.now().minusHours(1))
                post.complete(LocalDateTime.now())
            }
            PostStatus.CANCELED -> post.matchedCancel()
            PostStatus.EXPIRED -> post.expire()
        }
        return postRepository.save(post)
    }

    private fun statusOf(post: Post): PostStatus {
        return postRepository.findById(post.id!!).orElseThrow().postStatus
    }

    /* ── 취소할 수 있는 상태 ──────────────────────────────── */

    @ParameterizedTest
    @EnumSource(value = PostStatus::class, names = ["OPEN", "MATCHED"])
    @DisplayName("의뢰인이 본인의 모집 중·매칭된 공고를 취소하면 CANCELED 가 된다")
    fun 모집중_매칭된_공고는_취소된다(status: PostStatus) {
        val post = postWithStatus(client, status)

        postService.cancel(post.id!!, client.id)

        assertThat(statusOf(post)).isEqualTo(PostStatus.CANCELED)
    }

    /* ── 취소할 수 없는 상태 ──────────────────────────────── */

    @ParameterizedTest
    @EnumSource(value = PostStatus::class, names = ["OPEN", "MATCHED"], mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("동행 중·완료·이미 취소·만료된 공고는 취소할 수 없고 상태가 그대로다")
    fun 그_외_상태는_취소할_수_없다(status: PostStatus) {
        val post = postWithStatus(client, status)

        assertThatThrownBy { postService.cancel(post.id!!, client.id) }
            .isInstanceOf(InvalidException::class.java)

        assertThat(statusOf(post)).isEqualTo(status)
    }

    /* ── 회원 탈퇴·정지 시 일괄 취소 ───────────────────────── */

    @Test
    @DisplayName("[일괄 취소] 의뢰인의 OPEN·MATCHED 공고만 CANCELED 가 되고, 다른 상태의 공고는 그대로다")
    fun 일괄취소는_모집중_매칭된_공고만_취소한다() {
        val open = postWithStatus(client, PostStatus.OPEN)
        val matched = postWithStatus(client, PostStatus.MATCHED)
        val inProgress = postWithStatus(client, PostStatus.IN_PROGRESS)
        val completed = postWithStatus(client, PostStatus.COMPLETED)
        val canceled = postWithStatus(client, PostStatus.CANCELED)
        val expired = postWithStatus(client, PostStatus.EXPIRED)

        postService.cancelAllByClient(client.id)

        assertThat(statusOf(open)).isEqualTo(PostStatus.CANCELED)
        assertThat(statusOf(matched)).isEqualTo(PostStatus.CANCELED)
        assertThat(statusOf(inProgress)).isEqualTo(PostStatus.IN_PROGRESS)   // 동행 중인 공고는 건드리지 않는다
        assertThat(statusOf(completed)).isEqualTo(PostStatus.COMPLETED)
        assertThat(statusOf(canceled)).isEqualTo(PostStatus.CANCELED)
        assertThat(statusOf(expired)).isEqualTo(PostStatus.EXPIRED)
    }

    @Test
    @DisplayName("[일괄 취소] 공고가 여러 개여도 모두 취소된다 (반복문)")
    fun 일괄취소는_여러_공고를_모두_취소한다() {
        val posts = arrayOf(
            postWithStatus(client, PostStatus.OPEN),
            postWithStatus(client, PostStatus.OPEN),
            postWithStatus(client, PostStatus.MATCHED),
            postWithStatus(client, PostStatus.MATCHED),
        )

        postService.cancelAllByClient(client.id)

        assertThat(posts.map { statusOf(it) }).containsOnly(PostStatus.CANCELED)
    }

    @Test
    @DisplayName("[일괄 취소] 다른 의뢰인의 공고는 취소하지 않는다")
    fun 일괄취소는_다른_의뢰인의_공고를_건드리지_않는다() {
        val mine = postWithStatus(client, PostStatus.OPEN)
        val other = saveUser("cancelOther" + SEQ.incrementAndGet(), Role.CLIENT, "010-6664-%04d".format(SEQ.get()))
        val others = postWithStatus(other, PostStatus.OPEN)

        postService.cancelAllByClient(client.id)

        assertThat(statusOf(mine)).isEqualTo(PostStatus.CANCELED)
        assertThat(statusOf(others)).isEqualTo(PostStatus.OPEN)
    }

    @Test
    @DisplayName("[일괄 취소] 취소할 공고가 없으면 아무 일도 일어나지 않는다")
    fun 일괄취소_대상이_없어도_오류가_없다() {
        postService.cancelAllByClient(client.id)   // 공고 없음: 예외 없이 끝나야 한다
    }

    companion object {
        private val SEQ = AtomicInteger()
    }
}
