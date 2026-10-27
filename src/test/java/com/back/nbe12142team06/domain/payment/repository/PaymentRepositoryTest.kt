package com.back.nbe12142team06.domain.payment.repository

import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * PaymentRepository 쿼리의 조건절·정렬 검증.
 * 조회 대상이 사용자/공고 단위라서 다른 테스트가 남긴 데이터와 섞이지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentRepositoryTest {

    @field:Autowired private lateinit var paymentRepository: PaymentRepository
    @field:Autowired private lateinit var em: EntityManager

    private lateinit var client: User
    private lateinit var otherClient: User
    private lateinit var post: Post

    @BeforeEach
    fun setUp() {
        client = createUser("repoClient", "010-4444-0001")
        otherClient = createUser("repoOther", "010-4444-0002")
        em.persist(client)
        em.persist(otherClient)

        post = createPost(client, PostStatus.OPEN)
        em.persist(post)
    }

    private fun createUser(username: String, phoneNum: String): User {
        return User(
            username, "password1!", "$username@test.test", "김의뢰",
            Role.CLIENT, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
        )
    }

    private fun createPost(owner: User, postStatus: PostStatus): Post {
        return Post(
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
            recruitStartAt = LocalDateTime.now().minusDays(10),
            recruitEndAt = LocalDateTime.now().minusDays(5),
            escortStartAt = LocalDateTime.now().minusDays(3),
            escortEndAt = LocalDateTime.now().minusDays(3).plusHours(10),
            client = owner,
            postStatus = postStatus,
        )
    }

    private fun persistPayment(
        targetPost: Post,
        status: PaymentStatus,
        balanceAmount: BigDecimal = BigDecimal.valueOf(150_000),
        paymentKey: String? = null
    ): Payment {
        val payment = Payment(
            id = null,
            amount = BigDecimal.valueOf(150_000),
            hourlyPaySnapshot = 15_000,
            hours = BigDecimal.TEN,
            post = targetPost,
            method = if (status == PaymentStatus.READY) null else "CARD",
            paymentStatus = status,
            paymentKey = paymentKey,
            approvedAt = if (status == PaymentStatus.READY) null else LocalDateTime.now().minusDays(1),
            balanceAmount = balanceAmount
        )
        em.persist(payment)
        return payment
    }

    @Test
    @DisplayName("[PaymentRepository] 공고의 성공 결제만 잔액 내림차순으로 조회한다")
    fun findSuccessPayByPostId() {
        val donePayment = persistPayment(post, PaymentStatus.DONE, BigDecimal.valueOf(150_000))
        val partialPayment = persistPayment(post, PaymentStatus.PARTIAL_CANCELED, BigDecimal.valueOf(37_500))
        persistPayment(post, PaymentStatus.READY, BigDecimal.ZERO)
        persistPayment(post, PaymentStatus.CANCELED, BigDecimal.ZERO)
        persistPayment(post, PaymentStatus.DELETED, BigDecimal.ZERO)
        em.flush()
        em.clear()

        val result = paymentRepository.findSuccessPayByPostId(post.id!!)

        assertThat(result.map { it.requiredId })
            .containsExactly(donePayment.requiredId, partialPayment.requiredId)
    }

    @Test
    @DisplayName("[PaymentRepository] 사용자의 결제는 상태와 무관하게 모두 조회한다")
    fun findAllByUserId() {
        persistPayment(post, PaymentStatus.DONE)
        persistPayment(post, PaymentStatus.READY, BigDecimal.ZERO)
        val otherPost = createPost(otherClient, PostStatus.OPEN)
        em.persist(otherPost)
        persistPayment(otherPost, PaymentStatus.DONE)
        em.flush()
        em.clear()

        assertThat(paymentRepository.findAllByUserId(client.id)).hasSize(2)
        assertThat(paymentRepository.findAllByUserId(otherClient.id)).hasSize(1)
    }

    @Test
    @DisplayName("[PaymentRepository] 공고별 미결제 결제는 본인 것만 조회된다")
    fun findByPostIdAndUserIdAndReady() {
        val readyPayment = persistPayment(post, PaymentStatus.READY, BigDecimal.ZERO)
        em.flush()
        em.clear()

        assertThat(paymentRepository.findByPostIdAndUserIdAndReady(post.id!!, client.id)?.requiredId)
            .isEqualTo(readyPayment.requiredId)
        // 다른 사용자가 같은 공고로 조회하면 없다
        assertThat(paymentRepository.findByPostIdAndUserIdAndReady(post.id!!, otherClient.id)).isNull()
    }

    @Test
    @DisplayName("[PaymentRepository] 미결제(READY) 결제만 조회한다")
    fun findNotPaidByUserId() {
        val readyPayment = persistPayment(post, PaymentStatus.READY, BigDecimal.ZERO)
        persistPayment(post, PaymentStatus.DONE)
        em.flush()
        em.clear()

        val result = paymentRepository.findNotPaidByUserId(client.id)

        assertThat(result.map { it.requiredId }).containsExactly(readyPayment.requiredId)
    }

    @Test
    @DisplayName("[PaymentRepository] 동행 완료 공고의 미결제 추가 결제가 있으면 true")
    fun existsUnpaidExtraByUserId() {
        // 모집 중 공고의 미결제는 '추가 결제'가 아니다
        persistPayment(post, PaymentStatus.READY, BigDecimal.ZERO)
        em.flush()
        em.clear()
        assertThat(paymentRepository.existsUnpaidExtraByUserId(client.id)).isFalse()

        val completedPost = createPost(client, PostStatus.COMPLETED)
        em.persist(completedPost)
        persistPayment(completedPost, PaymentStatus.READY, BigDecimal.ZERO)
        em.flush()
        em.clear()

        assertThat(paymentRepository.existsUnpaidExtraByUserId(client.id)).isTrue()
    }

    @Test
    @DisplayName("[PaymentRepository] 공고 삭제로 취소 대기 중인 결제만 조회한다")
    fun findDeletedAllAndFindByPostIdAndPaymentStatus() {
        val deletedPayment = persistPayment(post, PaymentStatus.DELETED, BigDecimal.valueOf(150_000))
        persistPayment(post, PaymentStatus.DONE)
        em.flush()
        em.clear()

        assertThat(paymentRepository.findDeletedAll().map { it.requiredId })
            .contains(deletedPayment.requiredId)
        assertThat(paymentRepository.findByPostIdAndPaymentStatus(post.id!!, PaymentStatus.DELETED))
            .hasSize(1)
        assertThat(paymentRepository.findByPostIdAndPaymentStatus(post.id!!, PaymentStatus.READY))
            .isEmpty()
    }

    @Test
    @DisplayName("[PaymentRepository] 단건 조회는 공고·의뢰인을 함께 읽어 추가 쿼리가 없다")
    fun findByIdFetchJoin() {
        val payment = persistPayment(post, PaymentStatus.DONE)
        em.flush()
        em.clear()

        val found = paymentRepository.findByIdFetchJoin(payment.requiredId)!!

        // 페치 조인이 동작했다면 두 연관은 프록시가 아니라 이미 채워진 상태다.
        // 쿼리 수(SessionFactory.statistics)로 세지 않는 이유: 그 카운터는 SessionFactory 전체에
        // 하나뿐이어서 @Async 알림 스레드가 날린 쿼리까지 함께 세고, 간헐적으로 어긋난다.
        // post 가 프록시인 채로 post.client 를 건드리면 그 순간 초기화되므로 단정 순서가 중요하다.
        assertThat(Hibernate.isInitialized(found.post)).isTrue()
        assertThat(Hibernate.isInitialized(found.post.client)).isTrue()

        assertThat(found.post.title).isEqualTo("정형외과 동행 구합니다")
        assertThat(found.post.client.name).isEqualTo("김의뢰")
    }
}
