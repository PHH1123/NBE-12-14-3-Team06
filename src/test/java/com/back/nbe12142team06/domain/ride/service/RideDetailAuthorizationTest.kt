package com.back.nbe12142team06.domain.ride.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.ForbiddenException
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.SessionFactory
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * 이동 상세 조회 권한 검증.
 * 의뢰인과 그 공고에 지원한 동행 매니저만 볼 수 있고, 권한 확인에 드는 쿼리 수까지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RideDetailAuthorizationTest {

    @field:Autowired private lateinit var rideService: RideService
    @field:Autowired private lateinit var rideRepository: RideRepository
    @field:Autowired private lateinit var applicationRepository: ApplicationRepository
    @field:Autowired private lateinit var postRepository: PostRepository
    @field:Autowired private lateinit var userRepository: UserRepository
    @field:Autowired private lateinit var em: EntityManager
    @field:Autowired private lateinit var emf: EntityManagerFactory

    // 다른 테스트가 커밋해 둔 사용자와 username·email·전화번호가 겹치지 않도록 매 테스트마다 번호를 올린다
    // (공유 H2 testdb 를 쓰므로 고정값이면 실행 순서에 따라 USERS 유니크 제약을 위반한다)
    companion object {
        private val SEQ = AtomicInteger()
    }

    private lateinit var client: User
    private lateinit var escort: User
    private lateinit var stranger: User
    private var rideId: Long = 0

    @BeforeEach
    fun setUp() {
        val seq = SEQ.incrementAndGet()
        client = userRepository.save(
            createUser("rideClient$seq", Role.CLIENT, String.format("010-9101-%04d", seq))
        )
        escort = userRepository.save(
            createUser("rideEscort$seq", Role.ESCORT, String.format("010-9102-%04d", seq))
        )
        stranger = userRepository.save(
            createUser("rideStranger$seq", Role.ESCORT, String.format("010-9103-%04d", seq))
        )

        val post = postRepository.save(createPost(client))
        applicationRepository.save(
            Application(
                post = post,
                escort = escort,
                status = ApplicationStatus.ACCEPTED,
                acceptedPostId = post.id,
                activePostId = post.id,
            )
        )

        rideId = rideRepository.save(Ride(post = post, selected = RideSelect.TAXI)).requiredId
        rideRepository.save(Ride(post = post, selected = RideSelect.TAXI, direction = RideDirection.TO_HOME))

        // 조회 쿼리를 실제로 날리도록 영속성 컨텍스트를 비운다
        em.flush()
        em.clear()
    }

    private fun createUser(username: String, role: Role, phoneNum: String): User = User(
        username, "password1!", "$username@test.test",
        if (role == Role.ESCORT) "동행매니저" else "의뢰인",
        role, Gender.MALE, LocalDate.of(1990, 5, 20), phoneNum, "서울시"
    )

    private fun createPost(client: User): Post = Post(
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
        escortEndAt = LocalDateTime.now().minusDays(3).plusHours(4),
        postStatus = PostStatus.MATCHED,
        client = client,
    )

    @Test
    @DisplayName("[RideService] 의뢰인은 이동 상세를 조회할 수 있다")
    fun clientCanReadRide() {
        val ride = rideService.findById(client.id, rideId)

        assertThat(ride.requiredId).isEqualTo(rideId)
        assertThat(ride.direction).isEqualTo(RideDirection.TO_HOSPITAL)
    }

    @Test
    @DisplayName("[RideService] 그 공고에 지원한 동행 매니저도 이동 상세를 조회할 수 있다")
    fun appliedEscortCanReadRide() {
        val ride = rideService.findById(escort.id, rideId)

        assertThat(ride.requiredId).isEqualTo(rideId)
    }

    @Test
    @DisplayName("[RideService] 공고와 무관한 사용자는 이동 상세를 조회할 수 없다")
    fun strangerCannotReadRide() {
        assertThatThrownBy { rideService.findById(stranger.id, rideId) }
            .isInstanceOf(ForbiddenException::class.java)
            .hasMessage("이동 정보를 조회할 권한이 없습니다.")
    }

    @Test
    @DisplayName("[RideService] 동행 매니저 권한 확인은 쿼리 1건만 더 쓴다")
    fun escortAuthorizationCostsOneExtraQuery() {
        val statistics = emf.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        statistics.clear()
        rideService.findById(client.id, rideId)
        val clientQueries = statistics.prepareStatementCount

        em.clear()
        statistics.clear()
        rideService.findById(escort.id, rideId)
        val escortQueries = statistics.prepareStatementCount

        // 의뢰인: 이동 조회 + 공고 지연 로딩
        assertThat(clientQueries).isEqualTo(2)
        // 동행 매니저: 위 2건 + 지원 존재 확인 1건
        assertThat(escortQueries).isEqualTo(3)
    }
}
