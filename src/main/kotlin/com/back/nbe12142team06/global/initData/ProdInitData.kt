package com.back.nbe12142team06.global.initData

import com.back.nbe12142team06.domain.education.entity.EducationVideo
import com.back.nbe12142team06.domain.education.repository.EducationVideoRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.crypto.BlindIndexer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * 배포(prod)용 필수 초기 데이터 생성기.
 * 배포 환경은 ddl-auto: update 라 데이터가 유지되므로, 이미 있으면 다시 만들지 않습니다(재시작해도 안전).
 *
 * 넣는 것은 딱 두 가지입니다.
 * - 관리자 계정 1개: 회원가입 API(UserService.signUp)로는 ADMIN 을 만들 수 없어서 여기서 만듭니다.
 *   계정 정보는 코드에 두지 않고 .env(환경변수) → application-prod.yml(custom.admin.*) 로 받습니다.
 * - 교육 영상 1개: 동행 매니저가 교육을 이수(verified)해야 공고에 지원할 수 있으므로 필수입니다.
 *   영상 파일은 프론트 public/videos/ 에 있어야 합니다.
 */
@Configuration
@Profile("prod")
class ProdInitData(
    private val userRepository: UserRepository,
    private val educationVideoRepository: EducationVideoRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionManager: PlatformTransactionManager,
    private val blindIndexer: BlindIndexer,
) {
    @field:Value("\${custom.admin.username}")
    private lateinit var adminUsername: String

    @field:Value("\${custom.admin.password}")
    private lateinit var adminPassword: String

    @field:Value("\${custom.admin.email}")
    private lateinit var adminEmail: String

    @field:Value("\${custom.admin.phone-num}")
    private lateinit var adminPhoneNum: String

    @field:Value("\${custom.admin.name:관리자}")
    private lateinit var adminName: String

    @field:Value("\${custom.admin.region:서울}")
    private lateinit var adminRegion: String

    @Bean
    fun prodInitDataApplicationRunner(): ApplicationRunner = ApplicationRunner {
        TransactionTemplate(transactionManager).executeWithoutResult {
            initAdmin()
            initEducationVideos()
        }
    }

    internal fun initAdmin() {
        // 필수 환경변수 값이 비어 있으면 관리자 생성 전에 서버 시작을 중단한다.
        requireNotBlank(adminUsername, "ADMIN_USERNAME")
        requireNotBlank(adminPassword, "ADMIN_PASSWORD")
        requireNotBlank(adminEmail, "ADMIN_EMAIL")
        requireNotBlank(adminPhoneNum, "ADMIN_PHONE_NUM")

        if (userRepository.existsByUsername(adminUsername)) {
            log.info("[초기 데이터] 관리자 계정이 이미 있어 건너뜁니다. username={}", adminUsername)
            return
        }

        val adminEmailHash = blindIndexer.hashEmail(adminEmail)
        val adminPhoneNumHash = blindIndexer.hashPhoneNum(adminPhoneNum)

        if (userRepository.existsByEmailHash(adminEmailHash) ||
            userRepository.existsByPhoneNumHash(adminPhoneNumHash)
        ) {
            throw IllegalStateException("관리자 이메일 또는 전화번호를 이미 다른 회원이 사용 중입니다. .env 값을 확인해주세요.")
        }

        val admin = User(
            username = adminUsername,
            password = passwordEncoder.encode(adminPassword),
            email = adminEmail,
            name = adminName,
            role = Role.ADMIN,
            gender = Gender.MALE,
            birthDate = LocalDate.of(1985, 1, 1),
            phoneNum = adminPhoneNum,
            region = adminRegion,
        )
        userRepository.save(admin)

        // 비밀번호는 로그에 남기지 않는다.
        log.info("[초기 데이터] 관리자 계정을 만들었습니다. username={}", adminUsername)
    }

    internal fun initEducationVideos() {
        if (educationVideoRepository.count() > 0) {
            log.info("[초기 데이터] 교육 영상이 이미 있어 건너뜁니다.")
            return
        }

        educationVideoRepository.save(EducationVideo("동행 서비스 기본 교육", "/videos/sample_video.mp4", 60, true))
        log.info("[초기 데이터] 교육 영상을 등록했습니다.")
    }

    private fun requireNotBlank(value: String?, envName: String) {
        if (value.isNullOrBlank()) {
            throw IllegalStateException("$envName 환경변수가 비어 있습니다. .env 를 확인해주세요.")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProdInitData::class.java)
    }
}
