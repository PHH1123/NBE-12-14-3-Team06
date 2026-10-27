package com.back.nbe12142team06.global.initData

import com.back.nbe12142team06.domain.education.service.EducationService
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.ClientProfileRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoadTestInitDataTest {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var clientProfileRepository: ClientProfileRepository
    @Autowired private lateinit var escortProfileRepository: EscortProfileRepository
    @Autowired private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository
    @Autowired private lateinit var educationService: EducationService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private fun initData(userCount: Int) = LoadTestInitData(
        userRepository, clientProfileRepository, escortProfileRepository, userStatusHistoryRepository,
        educationService, passwordEncoder, transactionManager, userCount,
    )

    @Test
    @DisplayName("[부하 테스트 시드] 의뢰인·동행 매니저를 N명씩 만들고, 다시 실행해도 중복으로 만들지 않는다")
    fun 가상_사용자를_만든다() {
        val before = userRepository.count()

        initData(3).createUsers()

        assertThat(userRepository.count()).isEqualTo(before + 6)
        val client = userRepository.findByUsername("ltclient002").orElseThrow()
        val escort = userRepository.findByUsername("ltescort003").orElseThrow()
        assertThat(client.role).isEqualTo(Role.CLIENT)
        assertThat(escort.role).isEqualTo(Role.ESCORT)
        assertThat(passwordEncoder.matches("password1!", escort.password)).isTrue()
        // 동행 매니저는 인증까지 끝난 상태로 만들어져 바로 지원할 수 있다
        assertThat(escortProfileRepository.findById(escort.id!!).orElseThrow().verified).isTrue()

        // 두 번째 실행(서버 재시작 등): 같은 아이디는 건너뛰고 모자란 만큼만 만든다
        initData(5).createUsers()
        assertThat(userRepository.count()).isEqualTo(before + 10)
    }
}
