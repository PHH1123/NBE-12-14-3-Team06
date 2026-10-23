package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.dto.ApplicationAcceptResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationApplyResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationClientProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationEscortProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationListResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationProgressResponse
import com.back.nbe12142team06.domain.application.dto.MyApplicationResponse
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_ACCEPT
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_APPLY
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_CANCELED
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_PROGRESS_ARRIVED_HOME
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_PROGRESS_AT_HOSPITAL
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_REJECT
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_APPLICATION_UPDATED_PROGRESS
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_ACCEPT
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_APPLY
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_CANCELED
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_PROGRESS_ARRIVED_HOME
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_PROGRESS_AT_HOSPITAL
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_REJECT
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_UPDATED_PROGRESS_CLIENT
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_APPLICATION_UPDATED_PROGRESS_ESCORT
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.ClientProfileRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.EscortService
import com.back.nbe12142team06.global.exception.DuplicatedException
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import com.back.nbe12142team06.global.geo.GeoUtils
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Service
open class ApplicationService(
    applicationRepository: ApplicationRepository?,
    postRepository: PostRepository?,
    userRepository: UserRepository?,
    escortProfileRepository: EscortProfileRepository?,
    escortProgressLogRepository: EscortProgressLogRepository?,
    noShowPenaltyService: NoShowPenaltyService?,
    clientProfileRepository: ClientProfileRepository?,
    paymentRepository: PaymentRepository?,
    paymentService: PaymentService?,
    rideService: RideService?,
    escortService: EscortService?,
    noticeService: NoticeService?,
) {
    // Java의 생성자는 테스트에서 사용하지 않는 의존성에 null을 받을 수 있다.
    // Kotlin은 접근 시에만 기존 Java와 같이 null 참조 예외를 낸다.
    private val applicationRepositoryRef: ApplicationRepository? = applicationRepository
    private val applicationRepository: ApplicationRepository get() = applicationRepositoryRef!!

    private val postRepositoryRef: PostRepository? = postRepository
    private val postRepository: PostRepository get() = postRepositoryRef!!

    private val userRepositoryRef: UserRepository? = userRepository
    private val userRepository: UserRepository get() = userRepositoryRef!!

    private val escortProfileRepositoryRef: EscortProfileRepository? = escortProfileRepository
    private val escortProfileRepository: EscortProfileRepository get() = escortProfileRepositoryRef!!

    private val escortProgressLogRepositoryRef: EscortProgressLogRepository? = escortProgressLogRepository
    private val escortProgressLogRepository: EscortProgressLogRepository get() = escortProgressLogRepositoryRef!!

    private val noShowPenaltyServiceRef: NoShowPenaltyService? = noShowPenaltyService
    private val noShowPenaltyService: NoShowPenaltyService get() = noShowPenaltyServiceRef!!

    private val clientProfileRepositoryRef: ClientProfileRepository? = clientProfileRepository
    private val clientProfileRepository: ClientProfileRepository get() = clientProfileRepositoryRef!!

    private val paymentRepositoryRef: PaymentRepository? = paymentRepository
    private val paymentRepository: PaymentRepository get() = paymentRepositoryRef!!

    private val paymentServiceRef: PaymentService? = paymentService
    private val paymentService: PaymentService get() = paymentServiceRef!!

    private val rideServiceRef: RideService? = rideService
    private val rideService: RideService get() = rideServiceRef!!

    private val escortServiceRef: EscortService? = escortService
    private val escortService: EscortService get() = escortServiceRef!!

    private val noticeServiceRef: NoticeService? = noticeService
    private val noticeService: NoticeService get() = noticeServiceRef!!

    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    // 위치 기반 자동 진행 기준. 테스트에서 Spring 컨텍스트를 사용하지 않을 때는 Java와 같이 0.0 초기값이다.
    @field:Value("\${escort.geofence.hospital-radius-meters:200}")
    private var hospitalRadiusMeters: Double = 0.0

    @field:Value("\${escort.geofence.home-radius-meters:100}")
    private var homeRadiusMeters: Double = 0.0

    @field:Value("\${escort.geofence.max-accuracy-meters:50}")
    private var maxAccuracyMeters: Double = 0.0

    @Transactional
    open fun apply(postId: Long?, userId: Long?): ApplicationApplyResponse {
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }

        if (post.postStatus != PostStatus.OPEN) {
            throw InvalidException(21, "모집 중인 공고에만 지원할 수 있습니다.")
        }
        if (!post.isWithinPeriod(LocalDateTime.now())) {
            throw InvalidException(26, "모집 기간 내에만 지원할 수 있습니다.")
        }

        val escort = userRepository.findById(requireNotNull(userId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }
        if (escort.userStatus != UserStatus.ACTIVE) {
            throw ForbiddenException(31, "활성 상태의 회원만 지원할 수 있습니다.")
        }
        if (escort.role != Role.ESCORT) {
            throw InvalidException(22, "동행인만 공고에 지원할 수 있습니다.")
        }

        val escortProfile = escortProfileRepository.findById(requireNotNull(userId) { "The given id must not be null" })
            .orElseThrow { InvalidException(23, "동행 매니저 프로필을 등록한 후 지원할 수 있습니다.") }
        if (escortProfile.verified != true) {
            throw ForbiddenException(21, "교육 영상 시청을 완료한 후 지원할 수 있습니다.")
        }
        if (applicationRepository.existsByPostAndEscortAndStatusNot(post, escort, ApplicationStatus.CANCELED)) {
            throw DuplicatedException(21, "이미 지원한 공고입니다.")
        }

        val savedApplication = applicationRepository.save(Application(post, escort))
        log.info("[지원] applicationId={}, postId={}, escortId={}", savedApplication.id, postId, userId)

        noticeService.send(
            post.client.id,
            C_APPLICATION_APPLY.of(escort.name, post.title),
            U_APPLICATION_APPLY.of(post.id),
        )
        return ApplicationApplyResponse(savedApplication)
    }

    @Transactional(readOnly = true)
    open fun list(postId: Long?, userId: Long?, pageable: Pageable): Page<ApplicationListResponse> {
        val post = postRepository.findById(requireNotNull(postId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        if (post.client.id != userId) {
            throw ForbiddenException(22, "본인 공고의 지원 목록만 조회할 수 있습니다.")
        }
        return applicationRepository.findAllByPostIdWithEscort(postId, pageable)
            .map { ApplicationListResponse(it) }
    }

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    open fun accept(applicationId: Long?, userId: Long?): ApplicationAcceptResponse {
        // User → Post → Application 순서로 잠금 획득: Java 구현의 동시성 제어 순서를 유지한다.
        val escortId = applicationRepository.findEscortIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val escort = userRepository.findByIdWithLock(escortId)
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findByIdWithLock(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        if (post.client.id != userId) {
            throw ForbiddenException(23, "본인 공고의 지원만 승인할 수 있습니다.")
        }
        if (application.status == ApplicationStatus.REJECTED) {
            throw DuplicatedException(34, "이미 다른 동행매니저가 승인되었거나 동행 일정이 중복되어 승인할 수 없습니다.")
        }
        if (application.status != ApplicationStatus.PENDING) {
            throw InvalidException(24, "대기 중인 지원만 승인할 수 있습니다.")
        }
        if (escort.userStatus != UserStatus.ACTIVE) {
            throw ForbiddenException(32, "활성 상태의 동행매니저만 승인할 수 있습니다.")
        }
        if (post.postStatus != PostStatus.OPEN) {
            throw InvalidException(25, "모집 중인 공고만 승인할 수 있습니다.")
        }
        if (paymentRepository.findSuccessPayByPostId(postId).isEmpty()) {
            throw InvalidException(33, "결제 완료 공고만 승인할 수 있습니다.")
        }

        val acceptedApplications = applicationRepository.findAllByEscortAndStatus(escort, ApplicationStatus.ACCEPTED)
        if (acceptedApplications.any { isTimeOverlapping(post, it.post) }) {
            throw DuplicatedException(35, "이미 매칭된 다른 공고와 동행 시간이 겹칩니다.")
        }
        val samePostPendingApplications = applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.PENDING)
        val escortPendingApplications = applicationRepository.findAllByEscortAndStatus(escort, ApplicationStatus.PENDING)

        application.accept()
        post.match()
        samePostPendingApplications
            .filter { it.id != applicationId }
            .forEach { it.reject() }
        escortPendingApplications
            .filter { it.id != applicationId }
            .filter { isTimeOverlapping(post, it.post) }
            .forEach { it.reject() }

        log.info("[지원 수락] applicationId={}, postId={}, escortId={}, clientId={}", applicationId, post.id, escort.id, userId)
        noticeService.send(escort.id, C_APPLICATION_ACCEPT.of(post.title), U_APPLICATION_ACCEPT.of(applicationId))
        return ApplicationAcceptResponse(application)
    }

    @Transactional
    open fun reject(applicationId: Long?, userId: Long?) {
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findByIdWithLock(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        if (post.client.id != userId) {
            throw ForbiddenException(24, "본인 공고의 지원만 거절할 수 있습니다.")
        }
        if (application.status != ApplicationStatus.PENDING) {
            throw InvalidException(27, "대기 중인 지원만 거절할 수 있습니다.")
        }
        application.reject()
        log.info("[지원 거절] applicationId={}, clientId={}", applicationId, userId)
        noticeService.send(application.escort.id, C_APPLICATION_REJECT.of(post.title), U_APPLICATION_REJECT.of())
    }

    private fun isTimeOverlapping(firstPost: Post, secondPost: Post): Boolean =
        firstPost.escortStartAt.isBefore(secondPost.escortEndAt) &&
            firstPost.escortEndAt.isAfter(secondPost.escortStartAt)

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    open fun cancel(applicationId: Long?, userId: Long?) {
        // 승인과 동일한 User → Post → Application 잠금 순서.
        val escortId = applicationRepository.findEscortIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val escort = userRepository.findByIdWithLock(escortId)
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findByIdWithLock(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        if (escort.id != userId) {
            throw ForbiddenException(25, "본인이 지원한 내역만 취소할 수 있습니다.")
        }
        if (application.status == ApplicationStatus.PENDING) {
            application.cancel()
            log.info("[지원 취소] applicationId={}, escortId={}", applicationId, userId)
            return
        }
        if (application.status == ApplicationStatus.ACCEPTED) {
            if (post.postStatus == PostStatus.IN_PROGRESS || post.postStatus == PostStatus.COMPLETED) {
                throw InvalidException(36, "동행 진행 중이거나 완료된 공고는 지원을 취소할 수 없습니다.")
            }
            val escortProfile = escortProfileRepository.findById(userId)
                .orElseThrow { NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.") }
            application.noShow()
            escortProfile.increaseNoShowCount()
            val refreshed = escortService.refreshGrade(escortProfile)
            if (refreshed) {
                log.info("[등급 변경 완료] postId={}, escortId={} ", applicationId, userId)
            }
            noShowPenaltyService.noShow(application)

            if (LocalDateTime.now().isBefore(post.recruitEndAt)) {
                post.reopen()
            } else {
                post.matchedCancel()
                paymentService.cancel(post)
            }
            log.warn("[노쇼 취소] applicationId={}, postId={}, escortId={}", applicationId, post.id, userId)
            noticeService.send(
                post.client.id,
                C_APPLICATION_CANCELED.of(application.escort.name, post.title),
                U_APPLICATION_CANCELED.of(post.id),
            )
            return
        }
        throw InvalidException(28, "취소할 수 없는 지원 상태입니다.")
    }

    // 회원 탈퇴·정지 시 패널티 없이 지원 일괄 취소
    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    open fun cancelAllByEscort(escortId: Long?) {
        val applications = applicationRepository.findAllCancelableByEscortId(
            escortId,
            ApplicationStatus.PENDING,
            ApplicationStatus.ACCEPTED,
            PostStatus.OPEN,
            PostStatus.MATCHED,
        )
        for (candidate in applications) {
            // 조회 후 상태가 바뀔 수 있으므로 잠금을 잡은 다음 재검증한다.
            val post = postRepository.findByIdWithLock(candidate.post.id)
                .orElseThrow { NotFoundException(11, "공고가 없습니다.") }
            val application = applicationRepository.findByIdWithLock(candidate.id)
                .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
            if (application.status == ApplicationStatus.PENDING && post.postStatus == PostStatus.OPEN) {
                application.cancelWithoutPenalty()
                continue
            }
            if (application.status == ApplicationStatus.ACCEPTED && post.postStatus == PostStatus.MATCHED) {
                application.cancelWithoutPenalty()
                post.matchedCancel()
                paymentService.cancel(post)
                continue
            }
            throw InvalidException(35, "지원 또는 공고 상태가 변경되어 취소할 수 없습니다.")
        }
        log.info("[회원 탈퇴·정지 지원 일괄 취소] escortId={}, count={}", escortId, applications.size)
    }

    @Transactional(readOnly = true)
    open fun hasInProgressEscort(escortId: Long?): Boolean =
        applicationRepository.existsInProgressByEscortId(
            escortId,
            ApplicationStatus.ACCEPTED,
            PostStatus.IN_PROGRESS,
        )

    private fun currentProgressOf(application: Application): EscortProgress =
        escortProgressLogRepository.findTopByApplicationOrderByOccurredAtDescIdDesc(application)
            .map { it.progress }
            .orElse(EscortProgress.NOT_STARTED)

    private fun nextProgressOf(current: EscortProgress): EscortProgress? = when (current) {
        EscortProgress.NOT_STARTED -> EscortProgress.DEPARTED
        EscortProgress.DEPARTED -> EscortProgress.TO_HOSPITAL
        EscortProgress.TO_HOSPITAL -> EscortProgress.AT_HOSPITAL
        EscortProgress.AT_HOSPITAL -> EscortProgress.TO_HOME
        EscortProgress.TO_HOME -> EscortProgress.ARRIVED_HOME
        EscortProgress.ARRIVED_HOME -> null
    }

    // 버튼으로 동행 진행 단계를 변경한다.
    @Transactional
    open fun updateProgress(applicationId: Long?, userId: Long?, progress: EscortProgress) {
        updateProgress(applicationId, userId, progress, EscortProgressLog.ProgressSource.BUTTON)
    }

    // 버튼/GPS 공통 변경 경로. 잠금, 단계 검증, 로그 기록, 위치 삭제 순서 유지.
    private fun updateProgress(
        applicationId: Long?,
        userId: Long?,
        progress: EscortProgress,
        source: EscortProgressLog.ProgressSource,
    ) {
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findByIdWithLock(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        if (application.escort.id != userId) {
            throw ForbiddenException(26, "본인의 동행 진행 상태만 변경할 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED) {
            throw InvalidException(29, "승인된 지원만 동행 진행 상태를 변경할 수 있습니다.")
        }
        val nextProgress = nextProgressOf(currentProgressOf(application))
            ?: throw InvalidException(30, "이미 동행이 완료되었습니다.")
        if (progress != nextProgress) {
            throw InvalidException(31, "동행 진행 상태를 순서대로 변경해야 합니다.")
        }

        val occurredAt = LocalDateTime.now()
        escortProgressLogRepository.save(
            EscortProgressLog(application, progress, occurredAt, source),
        )
        rideService.move(application.activePostId, progress)

        // 의뢰인이 최종 완료 확인하기 전이라도 귀가 완료 시 위치 정보를 삭제한다.
        if (progress == EscortProgress.ARRIVED_HOME) {
            applicationRepository.clearLocation(applicationId)
        }
        if (progress == EscortProgress.DEPARTED) {
            post.startProgress(occurredAt)
        }
        log.info("[동행 진행상태 변경] applicationId={}, progress={}, escortId={}, source={}", applicationId, progress, userId, source)

        when (progress) {
            EscortProgress.AT_HOSPITAL -> noticeService.send(
                application.escort.id,
                C_APPLICATION_PROGRESS_AT_HOSPITAL.of(application.post.hospitalName),
                U_APPLICATION_PROGRESS_AT_HOSPITAL.of(applicationId),
            )
            EscortProgress.ARRIVED_HOME -> noticeService.send(
                application.post.client.id,
                C_APPLICATION_PROGRESS_ARRIVED_HOME.of(application.post.hospitalName),
                U_APPLICATION_PROGRESS_ARRIVED_HOME.of(applicationId, application.post.id),
            )
            else -> {
                noticeService.send(
                    application.post.client.id,
                    C_APPLICATION_UPDATED_PROGRESS.of(progress.description),
                    U_APPLICATION_UPDATED_PROGRESS_CLIENT.of(applicationId, application.post.id),
                )
                noticeService.send(
                    application.escort.id,
                    C_APPLICATION_UPDATED_PROGRESS.of(progress.description),
                    U_APPLICATION_UPDATED_PROGRESS_ESCORT.of(applicationId),
                )
            }
        }
    }

    @Transactional(readOnly = true)
    open fun getProgress(applicationId: Long?, userId: Long?): ApplicationProgressResponse {
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val isEscort = application.escort.id == userId
        val isClient = application.post.client.id == userId
        if (!isEscort && !isClient) {
            throw ForbiddenException(27, "본인의 동행 건만 진행 상태를 조회할 수 있습니다.")
        }
        return ApplicationProgressResponse(applicationId, currentProgressOf(application))
    }

    @Transactional(readOnly = true)
    open fun getEscortProfile(applicationId: Long?, userId: Long?): ApplicationEscortProfileResponse {
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = application.post
        if (post.client.id != userId) {
            throw ForbiddenException(20, "본인 공고의 지원자 프로필만 조회할 수 있습니다.")
        }
        val escort = application.escort
        val escortProfile = escortProfileRepository.findById(escort.id)
            .orElseThrow { NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.") }
        val rating = if (escortProfile.ratingCount == 0) {
            0.0
        } else {
            escortProfile.ratingSum.toDouble() / escortProfile.ratingCount
        }
        val age = Period.between(escort.birthDate, LocalDate.now()).years
        return ApplicationEscortProfileResponse(
            escort.id,
            escort.name,
            escortProfile.verified,
            escortProfile.intro,
            escortProfile.completedCount,
            rating,
            escortProfile.ratingCount,
            escortProfile.noShowCount,
            escortProfile.grade,
            age,
            escort.gender,
            escort.region,
        )
    }

    @Transactional(readOnly = true)
    open fun getClientProfile(applicationId: Long?, userId: Long?): ApplicationClientProfileResponse {
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        if (application.escort.id != userId) {
            throw ForbiddenException(28, "매칭된 의뢰인의 프로필만 조회할 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED) {
            throw ForbiddenException(33, "승인된 동행에서만 의뢰인 정보를 조회할 수 있습니다.")
        }
        val client = application.post.client
        val clientProfile = clientProfileRepository.findById(client.id)
            .orElseThrow { NotFoundException(2, "의뢰인 프로필이 존재하지 않습니다.") }
        return ApplicationClientProfileResponse(
            client.id,
            client.name,
            client.phoneNum,
            clientProfile.emergencyContactName,
            clientProfile.emergencyContactPhone,
            clientProfile.careNote,
        )
    }

    @Transactional(readOnly = true)
    open fun getMyApplications(userId: Long?): List<MyApplicationResponse> {
        val escort = userRepository.findById(requireNotNull(userId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(1, "회원 정보를 찾을 수 없습니다.") }
        if (escort.role != Role.ESCORT) {
            throw InvalidException(32, "동행 매니저만 지원 내역을 조회할 수 있습니다.")
        }
        return applicationRepository.findAllByEscortIdWithPost(userId).map { MyApplicationResponse(it) }
    }

    /**
     * 마지막 위치 하나만 저장한다. 5초 단위 API이므로 조건부 UPDATE로 DB 컬럼만 갱신한다.
     * 공고 락을 먼저 획득하여 귀가 완료와 위치 저장을 순차 처리한다.
     */
    @Transactional
    open fun saveLocation(applicationId: Long?, userId: Long?, request: ApplicationLocationRequest) {
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        if (application.escort.id != userId) {
            throw ForbiddenException(29, "본인이 진행하는 동행에서만 실시간 위치를 보낼 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED) {
            throw InvalidException(33, "승인된 동행에서만 실시간 위치를 보낼 수 있습니다.")
        }
        if (post.postStatus != PostStatus.IN_PROGRESS) {
            throw InvalidException(34, "동행이 진행 중일 때만 실시간 위치를 보낼 수 있습니다.")
        }

        val prevLat = application.lat
        val prevLng = application.lng
        val updated = applicationRepository.updateLocation(
            applicationId,
            request.lat,
            request.lng,
            request.accuracy,
            Instant.now(),
        )
        if (updated == 0) {
            throw InvalidException(34, "동행이 진행 중일 때만 실시간 위치를 보낼 수 있습니다.")
        }
        autoAdvanceByLocation(application, prevLat, prevLng, request)
    }

    /** 위치가 조건에 부합하면 버튼과 동일한 updateProgress 로직으로 자동 전이한다. */
    private fun autoAdvanceByLocation(
        application: Application,
        prevLat: BigDecimal?,
        prevLng: BigDecimal?,
        request: ApplicationLocationRequest,
    ) {
        if (request.accuracy != null && request.accuracy.toDouble() > maxAccuracyMeters) {
            return
        }
        val applicationId = application.id
        val escortId = application.escort.id
        val post = application.post

        val target = when (currentProgressOf(application)) {
            EscortProgress.DEPARTED -> if (
                isWithin(post.pickupLat, post.pickupLng, prevLat, prevLng, homeRadiusMeters) &&
                !isWithin(post.pickupLat, post.pickupLng, request.lat, request.lng, homeRadiusMeters)
            ) EscortProgress.TO_HOSPITAL else null
            EscortProgress.TO_HOSPITAL -> if (
                isWithin(post.hospitalLat, post.hospitalLng, request.lat, request.lng, hospitalRadiusMeters)
            ) EscortProgress.AT_HOSPITAL else null
            EscortProgress.TO_HOME -> if (
                isWithin(post.pickupLat, post.pickupLng, request.lat, request.lng, homeRadiusMeters)
            ) EscortProgress.ARRIVED_HOME else null
            else -> null
        } ?: return

        // 기존 엔티티의 상태가 캐시됐을 수 있으므로 비우고 잠금 조회로 재검증한다.
        entityManager.clear()
        try {
            updateProgress(applicationId, escortId, target, EscortProgressLog.ProgressSource.AUTO_GPS)
        } catch (e: InvalidException) {
            // 버튼 변경이 먼저 발생했다면 자동 전이만 건너뛰고 위치 저장은 성공시킨다.
            log.debug("[동행 진행 자동 변경 건너뜀] applicationId={}, target={}, reason={}", applicationId, target, e.message)
        }
    }

    private fun isWithin(
        baseLat: BigDecimal?,
        baseLng: BigDecimal?,
        lat: BigDecimal?,
        lng: BigDecimal?,
        radiusMeters: Double,
    ): Boolean {
        if (baseLat == null || baseLng == null || lat == null || lng == null) return false
        return GeoUtils.distanceMeters(baseLat, baseLng, lat, lng) <= radiusMeters
    }

    @Transactional(readOnly = true)
    open fun getLocation(applicationId: Long?, userId: Long?): ApplicationLocationResponse {
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val isEscort = application.escort.id == userId
        val isClient = application.post.client.id == userId
        if (!isEscort && !isClient) {
            throw ForbiddenException(30, "본인의 동행 건만 실시간 위치를 조회할 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED ||
            application.post.postStatus != PostStatus.IN_PROGRESS ||
            escortProgressLogRepository.existsByApplicationAndProgress(application, EscortProgress.ARRIVED_HOME)
        ) {
            throw NotFoundException(31, "아직 공유된 실시간 위치가 없습니다.")
        }
        if (application.lat == null || application.lng == null) {
            throw NotFoundException(31, "아직 공유된 실시간 위치가 없습니다.")
        }
        return ApplicationLocationResponse(application)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApplicationService::class.java)
    }
}
