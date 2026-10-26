package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileRequest
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository
import com.back.nbe12142team06.global.exception.DuplicatedException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    // 회원 생성 헬퍼
    private fun createUser(username: String, email: String, phoneNum: String): User {
        val savedUser = userRepository.save(
            User(
                username = username,
                password = "password1!",
                email = email,
                name = "테스트",
                role = Role.CLIENT,
                gender = Gender.FEMALE,
                birthDate = LocalDate.of(1960, 1, 1),
                phoneNum = phoneNum,
                region = "서울",
            )
        )
        userStatusHistoryRepository.save(UserStatusHistory.signup(savedUser))
        return savedUser
    }

    // 회원가입 요청 헬퍼 (테스트마다 다른 값만 넘긴다)
    private fun signUpRequest(
        username: String = "user1",
        email: String = "user@test.test",
        name: String = "유저1",
        role: Role = Role.CLIENT,
        phoneNum: String = "010-1234-5678",
        region: String = "서울",
    ) = UserSignUpRequest(
        username,
        "1234",
        email,
        name,
        role,
        Gender.MALE,
        LocalDate.of(1990, 5, 6),
        phoneNum,
        region,
    )

    // 첫 번째 회원 가입 후, 겹치는 값으로 두 번째 가입을 시도해 발생한 예외 반환
    private fun signUpDuplicated(duplicated: UserSignUpRequest): DuplicatedException {
        userService.signUp(signUpRequest())
        return assertThrows<DuplicatedException> { userService.signUp(duplicated) }
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 단순 저장")
    fun t1() {
        userService.signUp(signUpRequest())

        // 캐시 비우고 실제로 DB에서 조회
        em.flush()
        em.clear()

        val userCheck = userRepository.findByUsername("user1")!!
        assertThat(userCheck.username).isEqualTo("user1")
        assertThat(userCheck.password).isNotNull()
        assertThat(userCheck.email).isEqualTo("user@test.test")
        assertThat(userCheck.name).isEqualTo("유저1")
        assertThat(userCheck.role).isEqualTo(Role.CLIENT)
        assertThat(userCheck.gender).isEqualTo(Gender.MALE)
        assertThat(userCheck.birthDate).isEqualTo(LocalDate.of(1990, 5, 6))
        assertThat(userCheck.phoneNum).isEqualTo("010-1234-5678")
        assertThat(userCheck.region).isEqualTo("서울")
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 아이디로 가입 시 예외")
    fun t2() {
        // username = user1 중복
        val e = signUpDuplicated(
            signUpRequest(email = "userA@test.test", name = "유저A", phoneNum = "010-2345-6789", region = "경기")
        )

        assertThat(e.message).isEqualTo("이미 사용 중인 아이디입니다.")
        assertThat(e.statusCode).isEqualTo("409-1")
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 이메일로 가입 시 예외")
    fun t3() {
        // email = user@test.test 중복
        val e = signUpDuplicated(
            signUpRequest(username = "user2", name = "유저A", phoneNum = "010-2345-6789", region = "경기")
        )

        assertThat(e.message).isEqualTo("이미 사용 중인 이메일입니다.")
        assertThat(e.statusCode).isEqualTo("409-2")
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 전화번호로 가입 시 예외")
    fun t4() {
        // phoneNum = 010-1234-5678 중복
        val e = signUpDuplicated(
            signUpRequest(username = "user2", email = "user2@test.test", name = "유저A", region = "경기")
        )

        assertThat(e.message).isEqualTo("이미 사용 중인 전화번호입니다.")
        assertThat(e.statusCode).isEqualTo("409-3")
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 비밀번호 암호화")
    fun t5() {
        userService.signUp(signUpRequest())

        em.flush()
        em.clear()

        val userCheck = userRepository.findByUsername("user1")!!

        // 비밀번호가 평문인지 검증
        assertThat(userCheck.password).isNotEqualTo("1234")
        // 비밀번호가 지정한 방식으로 정확히 암호화되어 저장되어 있는지 검증
        assertThat(passwordEncoder.matches("1234", userCheck.password)).isTrue()
    }

    @Test
    @DisplayName("[UserService] 동행 매니저 프로필 생성")
    fun t6() {
        // 회원 생성
        val saved = userService.signUp(signUpRequest(role = Role.ESCORT))

        userService.createEscortProfile(
            saved.id,
            EscortProfileRequest(
                intro = "동행 매니저 입니다.",
                bankName = "오픈은행",
                accountHolder = "유저1",
                accountNumber = "123-000000-123",
            )
        )

        em.flush()
        em.clear()

        val profileCheck = escortProfileRepository.findById(saved.id).orElseThrow()

        assertThat(profileCheck.bankName).isEqualTo("오픈은행")
        assertThat(profileCheck.accountNumber).isEqualTo("123-000000-123")
    }

    @Test
    @DisplayName("[UserService] 정지 기간이 지나면 ACTIVE로 변경")
    fun t7() {
        val user = createUser("expired01", "expired01@test.com", "010-7000-0001")
        userStatusHistoryRepository.save(user.suspend(1L, "만료 테스트"))
        em.flush()
        em.clear()

        val released = userService.releaseExpiredSuspensions(LocalDateTime.now().plusDays(8))
        em.flush()
        em.clear()

        assertThat(released).isEqualTo(1)
        val found = userRepository.findById(user.id).orElseThrow()
        assertThat(found.userStatus).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    @DisplayName("[UserService] 정지 기간이 유효하면 SUSPEND 유지")
    fun t8() {
        val user = createUser("suspended01", "suspended01@test.com", "010-7000-0002")
        userStatusHistoryRepository.save(user.suspend(1L, "미만료 테스트"))
        em.flush()
        em.clear()

        val released = userService.releaseExpiredSuspensions(LocalDateTime.now().plusDays(6))
        em.flush()
        em.clear()

        assertThat(released).isZero()
        val found = userRepository.findById(user.id).orElseThrow()
        assertThat(found.userStatus).isEqualTo(UserStatus.SUSPENDED)
    }
}
