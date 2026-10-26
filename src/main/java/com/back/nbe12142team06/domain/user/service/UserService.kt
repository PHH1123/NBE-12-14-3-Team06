package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.service.ApplicationService
import com.back.nbe12142team06.domain.auth.repository.RefreshTokenRepository
import com.back.nbe12142team06.domain.education.service.EducationService
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.post.service.PostService
import com.back.nbe12142team06.domain.settlement.service.SettlementService
import com.back.nbe12142team06.domain.user.dto.admin.AdminUserProfileUpdateRequest
import com.back.nbe12142team06.domain.user.dto.login.UserLoginRequest
import com.back.nbe12142team06.domain.user.dto.profile.ClientProfileModifyRequest
import com.back.nbe12142team06.domain.user.dto.profile.ClientProfileRequest
import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileModifyRequest
import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileRequest
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.dto.user.UserProfileUpdateRequest
import com.back.nbe12142team06.domain.user.entity.ClientProfile
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.*
import com.back.nbe12142team06.global.crypto.BlindIndexer
import com.back.nbe12142team06.global.exception.*
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val authTokenService: AuthTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val clientProfileRepository: ClientProfileRepository,
    private val applicationRepository: ApplicationRepository,
    private val userStatusHistoryRepository: UserStatusHistoryRepository,
    private val educationService: EducationService,
    private val applicationService: ApplicationService,
    private val postService: PostService,
    private val paymentService: PaymentService,
    private val settlementService: SettlementService,
    private val escortGradeHistoryRepository: EscortGradeHistoryRepository,
    private val blindIndexer: BlindIndexer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── 회원가입 / 로그인 / 토큰 ─────────────────────────

    // 회원가입
    @Transactional
    fun signUp(request: UserSignUpRequest): User {
        // Admin으로 가입 불가
        if (request.role == Role.ADMIN) {
            throw InvalidException(1, "잘못된 요청입니다.")
        }

        // username 중복 검사
        if (userRepository.existsByUsername(request.username)) {
            throw DuplicatedException(DUPLICATED_USERNAME, "이미 사용 중인 아이디입니다.")
        }

        // email 중복 검사
        if (userRepository.existsByEmailHash(blindIndexer.hashEmail(request.email))) {
            throw DuplicatedException(DUPLICATED_EMAIL, "이미 사용 중인 이메일입니다.")
        }

        // phoneNum 중복 검사
        if (userRepository.existsByPhoneNumHash(blindIndexer.hashPhoneNum(request.phoneNum))) {
            throw DuplicatedException(DUPLICATED_PHONE_NUM, "이미 사용 중인 전화번호입니다.")
        }

        // 요청 값은 @Valid 검증(@NotBlank, @NotNull)을 통과했으므로 null 이 아니다
        val user = User(
            request.username!!,
            passwordEncoder.encode(request.password),  // 비밀번호 암호화
            request.email!!,
            request.name!!,
            request.role!!,
            request.gender!!,
            request.birthDate!!,
            request.phoneNum!!,
            request.region!!,
        )

        // 동시성 자체는 유니크로 막혀 있는데 그 때 500번이 나가버리기 때문에 이를 409로 감싸기만 했습니다.
        val savedUser = try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            throw DuplicatedException(4, "이미 사용 중인 회원 정보입니다.")
        }

        userStatusHistoryRepository.save(UserStatusHistory.signup(savedUser))
        log.info("[회원가입] userId={}, role={}, status={}", savedUser.id, savedUser.role, savedUser.userStatus)
        return savedUser
    }

    // 로그인
    @Transactional
    fun login(request: UserLoginRequest): User {
        val user = userRepository.findByUsername(request.username)
            .orElseThrow {
                log.warn("[로그인 실패] 존재하지 않는 아이디 username={}", request.username)
                UnauthorizedException(1, "아이디 또는 비밀번호가 올바르지 않습니다.")
            }

        try {
            checkPassword(request.password, user.password)
        } catch (e: UnauthorizedException) {
            log.warn("[로그인 실패] 비밀번호 불일치 userId={}", user.id)
            throw e
        }

        log.info("[로그인 성공] userId={}, role={}", user.id, user.role)
        return user
    }

    // access token 생성
    fun genAccessToken(user: User): String = authTokenService.genAccessToken(user)

    // access token 파싱
    fun payload(jwt: String): Map<String, Any> = authTokenService.payload(jwt)

    // 비밀번호 해싱값 대조
    fun checkPassword(rawPassword: String?, encodedPassword: String?) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw UnauthorizedException(2, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
    }

    // ── 내 정보 ─────────────────────────────────────────

    // 상세정보 조회
    @Transactional(readOnly = true)
    fun myProfile(id: Long): User = findMe(id)

    // 정지 계정의 마지막 상태 이력 (ACTIVE는 null 반환)
    @Transactional(readOnly = true)
    fun findCurrentSanction(user: User): UserStatusHistory? {
        if (user.isActive) return null
        return userStatusHistoryRepository.findTopByUserOrderByIdDesc(user)
            .filter { it.status == user.userStatus }
            .orElse(null)
    }

    // username 중복 검사
    @Transactional(readOnly = true)
    fun isUsernameAvailable(username: String): Boolean = !userRepository.existsByUsername(username)

    // email 중복 검사
    fun isEmailAvailable(email: String): Boolean =
        !userRepository.existsByEmailHash(blindIndexer.hashEmail(email))

    // 회원 정보 수정
    @Transactional
    fun updateMyProfile(id: Long, request: UserProfileUpdateRequest): User {
        val user = findMe(id)

        validateDuplicatedEmailAndPhone(id, request.email, request.phoneNum)

        // 요청 값은 @Valid 검증을 통과했으므로 null 이 아니다
        user.updateUser(
            passwordEncoder.encode(request.password),
            request.email!!,
            request.name!!,
            request.birthDate!!,
            request.phoneNum!!,
            request.region!!,
        )
        return userRepository.save(user)
    }

    // 일반 회원의 회원 탈퇴
    @Transactional
    fun deleteMyProfile(id: Long) {
        val user = userRepository.findByIdWithLock(id)
            ?: throw UnauthorizedException(3, "회원 정보를 찾을 수 없습니다. 다시 로그인해주세요.")

        if (user.role == Role.ADMIN) {
            throw InvalidException(3, "관리자는 자신의 계정을 탈퇴시킬 수 없습니다.")
        }

        // 동행 진행 중인 건, 미결제, 미정산 건이 존재하는 지 확인
        validateWithdrawable(user)
        // 공고 취소, 지원 취소
        cancelAllByUser(user)
        // 회원 정보, 프로필, 토큰 전부 삭제
        withdraw(user)

        log.info("[회원 탈퇴] userId={}", id)
    }

    // ── 의뢰인 프로필 ───────────────────────────────────

    // 의뢰인 프로필 생성
    @Transactional
    fun createClientProfile(userId: Long, request: ClientProfileRequest): ClientProfile {
        val user = findUser(userId)

        // 의뢰인만 프로필 생성 가능
        if (user.role != Role.CLIENT) {
            throw ForbiddenException(1, "의뢰인만 의뢰인 프로필을 생성할 수 있습니다.")
        }

        if (clientProfileRepository.existsById(userId)) {
            throw DuplicatedException(5, "이미 의뢰인 프로필이 존재합니다.")
        }


        val profile = ClientProfile(
            user,
            request.emergencyContactName!!,   // @NotBlank 검증 통과
            request.emergencyContactPhone!!,
            careNoteOrDefault(request.careNote),
        )

        return clientProfileRepository.save(profile)
    }

    // 의뢰인 자기 자신 프로필 조회
    @Transactional(readOnly = true)
    fun getClientProfile(clientId: Long): ClientProfile = findClientProfile(clientId)

    // 의뢰인 자기 자신 프로필 수정
    @Transactional
    fun updateClientProfile(id: Long, request: ClientProfileModifyRequest): ClientProfile {
        val clientProfile = findClientProfile(id)

        clientProfile.updateProfile(
            request.emergencyContactName!!,   // @NotBlank 검증 통과
            request.emergencyContactPhone!!,
            careNoteOrDefault(request.careNote),
        )
        return clientProfileRepository.save(clientProfile)
    }

    // [관리자, 매칭된 동행 매니저] 의뢰인 프로필 조회
    @Transactional(readOnly = true)
    fun getClientProfile(requesterId: Long, clientId: Long): ClientProfile {
        val requester = findMe(requesterId)

        when (requester.role) {
            Role.ADMIN -> Unit
            Role.ESCORT ->
                if (!applicationRepository.hasActiveMatching(requesterId, clientId)) {
                    throw ForbiddenException(2, "매칭된 의뢰인의 프로필만 조회할 수 있습니다.")
                }
            else -> throw ForbiddenException(3, "조회 권한이 없습니다.")
        }

        return findClientProfile(clientId)
    }

    // ── 동행인 프로필 ───────────────────────────────────

    // 동행인 프로필 생성
    @Transactional
    fun createEscortProfile(userId: Long, request: EscortProfileRequest): EscortProfile {
        val user = findUser(userId)

        // 동행 매니저만 동행인 프로필 생성 가능
        if (user.role != Role.ESCORT) {
            throw ForbiddenException(3, "동행 매니저만 동행인 프로필을 생성할 수 있습니다.")
        }

        if (escortProfileRepository.existsById(userId)) {
            throw DuplicatedException(6, "이미 존재하는 동행 매니저 프로필입니다.")
        }

        // 프로필 생성 및 회원 연결
        val savedProfile = escortProfileRepository.save(
            EscortProfile(user, request.intro!!, request.bankName!!, request.accountHolder!!, request.accountNumber!!)  // @NotBlank 검증 통과
        )

        // 모든 교육 영상에 대한 진행 상황 생성
        educationService.createProgresses(savedProfile)

        return savedProfile
    }

    // 동행인 프로필 조회
    // findByIdWithUser 로 user 를 함께 가져옵니다. 응답 DTO(EscortProfileResponse 등)가
    // escortProfile.user 를 쓰는데, 트랜잭션이 끝난 뒤(컨트롤러)에 지연 로딩된 user 에
    // 접근하면 LazyInitializationException 이 나기 때문입니다.
    @Transactional(readOnly = true)
    fun getEscortProfile(escortId: Long): EscortProfile = findEscortProfileWithUser(escortId)

    // 동행인 프로필 수정
    @Transactional
    fun updateEscortProfile(escortId: Long, request: EscortProfileModifyRequest): EscortProfile {
        val escortProfile = findEscortProfileWithUser(escortId)

        escortProfile.updateProfile(request.intro!!, request.bankName!!, request.accountHolder!!, request.accountNumber!!)  // @NotBlank 검증 통과
        return escortProfileRepository.save(escortProfile)
    }

    // ── 관리자 ──────────────────────────────────────────

    // [ADMIN] 회원 정보 조회 (탈퇴한 회원 정보도 가능)
    @Transactional(readOnly = true)
    fun findByIdIncludingDeleted(userId: Long): User =
        userRepository.findByIdIncludingDeleted(userId)
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }

    // [ADMIN] 회원 목록 조회 (탈퇴한 회원 정보도 가능)
    @Transactional(readOnly = true)
    fun findAllUsersIncludingDeleted(page: Int, size: Int): Page<User> {
        if (page < 0) {
            throw InvalidException(4, "페이지 번호는 음수일 수 없습니다.")
        }
        return userRepository.findAllIncludingDeleted(PageRequest.of(page, size))
    }

    // [관리자] 회원 정보 수정
    @Transactional
    fun updateUserByAdmin(userId: Long, request: AdminUserProfileUpdateRequest): User {
        val user = findByIdIncludingDeleted(userId)

        if (user.isDeleted) {
            throw InvalidException(2, "탈퇴한 회원의 정보는 수정할 수 없습니다.")
        }

        validateDuplicatedEmailAndPhone(userId, request.email, request.phoneNum)

        // 요청 값은 @Valid 검증을 통과했으므로 null 이 아니다
        user.updateUser(
            user.password,  // 기존 비밀번호 해시 유지
            request.email!!,
            request.name!!,
            request.birthDate!!,
            request.phoneNum!!,
            request.region!!,
        )
        return userRepository.save(user)
    }

    // [ADMIN] 관리자용 회원 탈퇴
    @Transactional
    fun deleteUser(adminId: Long, userId: Long) {
        val user = userRepository.findByIdIncludingDeletedWithLock(userId)
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }

        // 관리자는 자신에 대한 탈퇴 불가
        if (adminId == userId) {
            throw InvalidException(3, "관리자는 자신의 계정을 탈퇴시킬 수 없습니다.")
        }

        // 이미 탈퇴한 회원은 탈퇴 불가
        if (user.isDeleted) {
            throw InvalidException(5, "이미 탈퇴한 회원입니다.")
        }

        // 동행 진행 중인 건, 미결제, 미정산 건이 존재하는 지 확인
        validateWithdrawable(user)
        // 공고 취소, 지원 취소
        cancelAllByUser(user)
        // 회원 정보, 프로필, 토큰 전부 삭제
        withdraw(user)
    }

    // [관리자] 회원 계정에 기간 정지 패널티 부여
    @Transactional
    fun suspendUser(adminId: Long, userId: Long, reason: String?): UserStatusHistory {
        val user = findPenaltyTarget(userId)
        val history = userStatusHistoryRepository.save(user.suspend(adminId, reason!!))

        // 공고 취소, 지원 취소
        cancelAllByUser(user)

        log.info("[회원 기간 정지] adminId={}, userId={}", adminId, userId)
        return history
    }

    // [관리자] 회원 계정에 영구 정지 패널티 부여
    @Transactional
    fun banUser(adminId: Long, userId: Long, reason: String?): UserStatusHistory {
        val user = findPenaltyTarget(userId)
        val history = userStatusHistoryRepository.save(user.ban(adminId, reason!!))

        // 공고 취소, 지원 취소
        cancelAllByUser(user)

        log.info("[회원 영구 정지] adminId={}, userId={}", adminId, userId)
        return history
    }

    // [관리자] 정지된 계정을 활성 상태로 변경
    @Transactional
    fun releaseUser(adminId: Long, userId: Long, reason: String?): UserStatusHistory {
        val user = findUser(userId)
        val history = userStatusHistoryRepository.save(user.release(adminId, reason!!))

        log.info("[회원 정지 해제] adminId={}, userId={}", adminId, userId)
        return history
    }

    // 정지 기간이 지난 회원을 ACTIVE 로 되돌린다 (스케줄러에서 호출)
    @Transactional
    fun releaseExpiredSuspensions(now: LocalDateTime): Int {
        val released = userRepository.findAllByUserStatus(UserStatus.SUSPENDED).count { user ->
            val endAt = userStatusHistoryRepository.findTopByUserOrderByIdDesc(user).orElse(null)?.endAt

            when {
                // 기록이 없는 유저, 기간 정지인데 만료기간이 없는 기록 체크
                endAt == null -> {
                    log.warn("[정지 만료 해제] 이력 불일치 userId={}", user.id)
                    false
                }
                // 정지 기간이 만료 시 해제 (user의 상태 -> ACTIVE, userStatusHistory에 해당 유저 ACTIVE로 변경 기록 생성)
                endAt.isBefore(now) -> {
                    userStatusHistoryRepository.save(user.expire())
                    true
                }
                else -> false
            }
        }

        log.info("[정지 만료 해제] {}명", released)
        return released
    }

    // ── 내부 공통 ───────────────────────────────────────

    // 로그인한 본인 조회 (없으면 재로그인 유도)
    private fun findMe(id: Long): User =
        userRepository.findById(id)
            .orElseThrow { UnauthorizedException(3, "회원 정보를 찾을 수 없습니다. 다시 로그인해주세요.") }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }

    private fun findClientProfile(clientId: Long): ClientProfile =
        clientProfileRepository.findById(clientId)
            .orElseThrow { NotFoundException(2, "의뢰인 프로필이 존재하지 않습니다.") }

    private fun findEscortProfileWithUser(escortId: Long): EscortProfile =
        escortProfileRepository.findByIdWithUser(escortId)
            .orElseThrow { NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.") }

    // 정지 대상 회원 조회 (잠금) + 관리자 정지 불가 검사
    private fun findPenaltyTarget(userId: Long): User {
        val user = userRepository.findByIdWithLock(userId)
            ?: throw NotFoundException(1, "회원 정보를 찾을 수 없습니다.")

        // 관리자에게 정지 불가
        if (user.role == Role.ADMIN) {
            throw InvalidException(6, "관리자의 계정을 정지시킬 수 없습니다.")
        }
        return user
    }

    // 특이사항이 비어 있으면 기본 문구로 채운다
    private fun careNoteOrDefault(careNote: String?): String =
        careNote?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_PROFILE_CARE_NOTE

    // 내 정보 수정·관리자 수정 공통 중복 검사
    private fun validateDuplicatedEmailAndPhone(id: Long, email: String?, phoneNum: String?) {
        if (userRepository.existsByEmailHashAndIdNot(blindIndexer.hashEmail(email), id)) {
            throw DuplicatedException(DUPLICATED_EMAIL, "이미 사용 중인 이메일입니다.")
        }
        if (userRepository.existsByPhoneNumHashAndIdNot(blindIndexer.hashPhoneNum(phoneNum), id)) {
            throw DuplicatedException(DUPLICATED_PHONE_NUM, "이미 사용 중인 전화번호입니다.")
        }
    }

    // 탈퇴 가능 여부 판단
    private fun validateWithdrawable(user: User) {
        val userId = user.id

        when (user.role) {
            Role.CLIENT -> {
                if (postService.hasInProgressPost(userId)) throw InvalidException(7, "진행 중인 동행이 있어 탈퇴 할 수 없습니다.")
                if (paymentService.hasUnpaidExtraPayment(userId)) throw InvalidException(10, "미결제 공고가 있어 탈퇴 할 수 없습니다.")
            }
            Role.ESCORT -> {
                if (applicationService.hasInProgressEscort(userId)) throw InvalidException(7, "진행 중인 동행이 있어 탈퇴 할 수 없습니다.")
                if (settlementService.hasUnsettled(userId)) throw InvalidException(101, "미정산이 동행이 있어 탈퇴 할 수 없습니다.")
            }
            Role.ADMIN -> Unit
        }
    }

    // 탈퇴, 정지 시 공고, 지원 취소 및 삭제
    private fun cancelAllByUser(user: User) {
        when (user.role) {
            Role.CLIENT -> postService.cancelAllByClient(user.id)
            Role.ESCORT -> applicationService.cancelAllByEscort(user.id)
            Role.ADMIN -> Unit
        }
    }

    // 프로필 삭제, 리프레시 토큰 폐기, 회원 정보 삭제
    private fun withdraw(user: User) {
        val userId = user.id

        when (user.role) {
            Role.CLIENT -> clientProfileRepository.deleteByUserId(userId)
            Role.ESCORT -> {
                educationService.deleteProgresses(userId)
                escortGradeHistoryRepository.deleteAllByEscortUserId(userId)
                escortProfileRepository.deleteByUserId(userId)
            }
            Role.ADMIN -> Unit
        }

        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach { it.revoke() }

        user.deleteUser()
        userRepository.save(user)
    }

    companion object {
        private const val DUPLICATED_USERNAME = 1
        private const val DUPLICATED_EMAIL = 2
        private const val DUPLICATED_PHONE_NUM = 3

        private const val DEFAULT_CLIENT_PROFILE_CARE_NOTE = "특이사항 없음"
    }
}