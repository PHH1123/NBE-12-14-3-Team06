package com.back.nbe12142team06.global.initData

import com.back.nbe12142team06.domain.education.service.EducationService
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
 */
@Configuration
@Profile("loadtest")
class LoadTestInitData(
    private val userRepository: UserRepository,
    private val clientProfileRepository: ClientProfileRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val userStatusHistoryRepository: UserStatusHistoryRepository,
    private val educationService: EducationService,
    private val passwordEncoder: PasswordEncoder,
    private val transactionManager: PlatformTransactionManager,
    @Value("\${loadtest.user-count:100}") private val userCount: Int,
) {

    @Bean
    @DependsOn("baseInitDataApplicationRunner")
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun loadTestInitDataApplicationRunner(): ApplicationRunner = ApplicationRunner {
        TransactionTemplate(transactionManager).executeWithoutResult { createUsers() }
    }

    internal fun createUsers() {
        // 비밀번호 해시는 비용이 커서 한 번만 만들어 모두에게 쓴다.
        val encodedPassword = passwordEncoder.encode(PASSWORD)
        var createdClients = 0
        var createdEscorts = 0

        for (i in 1..userCount) {
            val clientName = "ltclient%03d".format(i)
            if (!userRepository.findByUsername(clientName).isPresent) {
                val client = save(newUser(clientName, "부하의뢰$i", Role.CLIENT, Gender.FEMALE, 1955, "010-5%03d-0001".format(i), encodedPassword))
                clientProfileRepository.save(ClientProfile(client, "부하보호$i", "010-9%03d-0001".format(i), null))
                createdClients++
            }

            val escortName = "ltescort%03d".format(i)
            if (!userRepository.findByUsername(escortName).isPresent) {
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
    }
}
