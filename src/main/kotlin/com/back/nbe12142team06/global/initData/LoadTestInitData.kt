package com.back.nbe12142team06.global.initData

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.education.service.EducationService
import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import com.back.nbe12142team06.domain.user.entity.ClientProfile
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.ClientProfileRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 부하 테스트(k6)용 가상 사용자 생성기.
 * `--spring.profiles.active=dev,loadtest` 로 띄우면 기본 시드(BaseInitData) 뒤에 의뢰인·동행 매니저를 각각 N명 만든다.
 * 이미 만들어져 있으면(같은 아이디가 있으면) 다시 만들지 않는다.
 *
 * - 아이디: ltclient001 ~ / ltescort001 ~ (비밀번호는 전부 "password1!")
 * - 동행 매니저는 교육 이수 정보와 인증(verified)까지 채워 두어 바로 지원할 수 있다.
 * - 사람 수는 loadtest.user-count (기본 100)
 * - 번호가 같은 의뢰인·동행 매니저는 "매칭 완료(MATCHED) 공고 + 승인된 지원" 한 쌍으로 묶어 둔다.
 *   (실시간 위치·진행 상태 부하 테스트용. 지원 목록 조회(GET /applications/me)로 applicationId 를 찾는다.)
 * - 목록·검색 테스트용으로 결제 완료된 모집 중 공고를 loadtest.open-post-count(기본 1000)건 만든다.
 */
@Configuration
@Profile("loadtest")
class LoadTestInitData(
    private val userRepository: UserRepository,
    private val clientProfileRepository: ClientProfileRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val userStatusHistoryRepository: UserStatusHistoryRepository,
    private val educationService: EducationService,
    private val postRepository: PostRepository,
    private val paymentRepository: PaymentRepository,
    private val applicationRepository: ApplicationRepository,
    private val rideRepository: RideRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionManager: PlatformTransactionManager,
    @Value("\${loadtest.user-count:100}") private val userCount: Int,
    @Value("\${loadtest.open-post-count:1000}") private val openPostCount: Int,
) {

    @Bean
    @DependsOn("baseInitDataApplicationRunner")
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun loadTestInitDataApplicationRunner(): ApplicationRunner = ApplicationRunner {
        TransactionTemplate(transactionManager).executeWithoutResult {
            createUsers()
            createMatchedPairs()
            createOpenPosts()
        }
    }

    internal fun createUsers() {
        // 비밀번호 해시는 비용이 커서 한 번만 만들어 모두에게 쓴다.
        val encodedPassword = passwordEncoder.encode(PASSWORD)
        var createdClients = 0
        var createdEscorts = 0

        for (i in 1..userCount) {
            val clientName = "ltclient%03d".format(i)
            if (userRepository.findByUsername(clientName) == null) {
                val client = save(newUser(clientName, "부하의뢰$i", Role.CLIENT, Gender.FEMALE, 1955, "010-5%03d-0001".format(i), encodedPassword))
                clientProfileRepository.save(ClientProfile(client, "부하보호$i", "010-9%03d-0001".format(i), null))
                createdClients++
            }

            val escortName = "ltescort%03d".format(i)
            if (userRepository.findByUsername(escortName) == null) {
                val escort = save(newUser(escortName, "부하동행$i", Role.ESCORT, Gender.MALE, 1990, "010-6%03d-0001".format(i), encodedPassword))
                val profile = escortProfileRepository.save(
                    EscortProfile(escort, "부하 테스트용 동행 매니저 ${i}입니다.", "국민은행", "부하동행$i", "123-456-789012")
                )
                educationService.createProgresses(profile)
                escortProfileRepository.verify(escort.id!!, LocalDateTime.now())
                createdEscorts++
            }
        }
        log.info("[부하 테스트 시드] 의뢰인 {}명, 동행 매니저 {}명을 새로 만들었습니다. (비밀번호 {})", createdClients, createdEscorts, PASSWORD)
    }

    // 번호가 같은 의뢰인·동행 매니저를 매칭 완료 공고 + 승인된 지원으로 묶는다. 이미 공고가 있는 의뢰인은 건너뛴다.
    internal fun createMatchedPairs() {
        var created = 0
        for (i in 1..userCount) {
            val client = userRepository.findByUsername("ltclient%03d".format(i)) ?: continue
            val escort = userRepository.findByUsername("ltescort%03d".format(i)) ?: continue
            if (postRepository.existsByClientIdAndTitle(client.id!!, pairTitle(i))) continue

            val post = savePost(client, pairTitle(i), "서울", HOSPITALS[i % HOSPITALS.size], i)
            val application = applicationRepository.save(Application(post, escort))
            application.accept()
            post.match()
            created++
        }
        log.info("[부하 테스트 시드] 매칭 완료 공고+승인된 지원 {}쌍을 새로 만들었습니다.", created)
    }

    // 목록·검색 부하 테스트용 모집 중 공고. 이미 충분히 있으면 만들지 않는다.
    internal fun createOpenPosts() {
        val clients = (1..userCount).mapNotNull { userRepository.findByUsername("ltclient%03d".format(it)) }
        if (clients.isEmpty()) return
        val existing = postRepository.countByTitleStartingWith(OPEN_TITLE_PREFIX).toInt()
        for (n in existing + 1..openPostCount) {
            savePost(clients[n % clients.size], "$OPEN_TITLE_PREFIX $n", REGIONS[n % REGIONS.size], HOSPITALS[n % HOSPITALS.size], n)
        }
        log.info("[부하 테스트 시드] 모집 중 공고 {}건을 새로 만들었습니다.", (openPostCount - existing).coerceAtLeast(0))
    }

    private fun pairTitle(i: Int) = "부하 테스트 매칭 공고 $i"

    // 결제 완료(DONE) 상태의 모집 중 공고와 이동수단(왕복)을 만든다.
    private fun savePost(client: User, title: String, region: String, hospital: String, seq: Int): Post {
        val now = LocalDateTime.now()
        val post = postRepository.save(
            Post(
                client = client,
                title = title,
                content = "부하 테스트용 공고입니다. $hospital 진료 동행을 부탁드립니다.",
                patientNote = null,
                region = region,
                hospitalName = hospital,
                hospitalAddress = "$region 테스트구 테스트로 $seq",
                hospitalLat = BigDecimal("37.5665000"),
                hospitalLng = BigDecimal("126.9780000"),
                pickupAddress = "$region 테스트구 자택로 $seq",
                pickupLat = BigDecimal("37.5665000"),
                pickupLng = BigDecimal("126.9780000"),
                hourlyPay = 10_000 + (seq % 10) * 1_000,
                recruitStartAt = now.minusHours(1),
                recruitEndAt = now.plusDays(10),
                escortStartAt = now.plusDays(11).plusHours((seq % 8).toLong()),
                escortEndAt = now.plusDays(11).plusHours((seq % 8) + 3L),
                postStatus = PostStatus.OPEN,
            )
        )
        rideRepository.save(Ride(id = null, direction = RideDirection.TO_HOSPITAL, status = RideStatus.ACCEPTED, selected = RideSelect.TAXI, post = post))
        rideRepository.save(Ride(id = null, direction = RideDirection.TO_HOME, status = RideStatus.ACCEPTED, selected = RideSelect.TAXI, post = post))
        paymentRepository.save(
            Payment(
                id = null,
                amount = post.totalPay,
                hourlyPaySnapshot = post.hourlyPay,
                hours = post.escortHours,
                post = post,
                method = "카드",
                paymentStatus = PaymentStatus.DONE,
                orderId = "LT-ORDER-%s".format(post.id),
                paymentKey = "LT-PAYKEY-%s".format(post.id),
                approvedAt = now,
                balanceAmount = post.totalPay,
            )
        )
        return post
    }

    private fun save(user: User): User {
        val saved = userRepository.save(user)
        userStatusHistoryRepository.save(UserStatusHistory.signup(saved))
        return saved
    }

    private fun newUser(username: String, name: String, role: Role, gender: Gender, birthYear: Int, phone: String, encodedPassword: String?) =
        User(
            username = username,
            password = encodedPassword,
            email = "$username@example.com",
            name = name,
            role = role,
            gender = gender,
            birthDate = LocalDate.of(birthYear, 1, 1),
            phoneNum = phone,
            region = "서울",
        )

    companion object {
        private val log = LoggerFactory.getLogger(LoadTestInitData::class.java)
        const val PASSWORD = "password1!"
        private const val OPEN_TITLE_PREFIX = "부하 테스트 모집 공고"
        private val REGIONS = listOf("서울", "부산", "경기", "인천", "대구", "광주")
        private val HOSPITALS = listOf("서울대병원", "세브란스병원", "아산병원", "삼성서울병원", "성모병원", "고려대병원", "이대병원", "중앙대병원")
    }
}
