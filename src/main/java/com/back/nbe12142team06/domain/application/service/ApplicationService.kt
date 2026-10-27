package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.dto.ApplicationAcceptResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationApplyResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationClientProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationEscortProfileResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationListResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationResponse
import com.back.nbe12142team06.domain.application.dto.ApplicationProgressResponse
import com.back.nbe12142team06.domain.application.dto.LocationPoint
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
import kotlin.math.roundToInt

@Service
open class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val escortProgressLogRepository: EscortProgressLogRepository,
    private val noShowPenaltyService: NoShowPenaltyService,
    private val clientProfileRepository: ClientProfileRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val rideService: RideService,
    private val escortService: EscortService,
    private val noticeService: NoticeService,
) {
    // 의뢰인과 동행 매니저가 "가깝다"고 보는 반경(미터)과, 이보다 오래된 위치는 믿지 않는 기준(초).
    // 테스트에서 Spring 컨텍스트를 사용하지 않을 때는 0 초기값이다.
    @field:Value("\${escort.proximity.radius-meters:100}")
    private var proximityRadiusMeters: Double = 0.0

    @field:Value("\${escort.proximity.stale-seconds:30}")
    private var staleSeconds: Long = 0

    @Transactional
    open fun apply(postId: Long?, userId: Long?): ApplicationApplyResponse {
        val post = postRepository.findByIdWithLock(requireNotNull(postId) { "The given id must not be null" })
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
            U_APPLICATION_APPLY.of(requireNotNull(post.id) { "Post ID must not be null" }),
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
        noticeService.send(escort.id, C_APPLICATION_ACCEPT.of(post.title), U_APPLICATION_ACCEPT.of(requireNotNull(applicationId) { "Application ID must not be null" }))
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
                U_APPLICATION_CANCELED.of(requireNotNull(post.id) { "Post ID must not be null" }),
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
            val post = postRepository.findByIdWithLock(requireNotNull(candidate.post.id) { "Post ID must not be null" })
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
            .orElse(EscortProgress.NOT_STARTED) ?: EscortProgress.NOT_STARTED

    private fun nextProgressOf(current: EscortProgress): EscortProgress? = when (current) {
        EscortProgress.NOT_STARTED -> EscortProgress.DEPARTED
        EscortProgress.DEPARTED -> EscortProgress.TO_HOSPITAL
        EscortProgress.TO_HOSPITAL -> EscortProgress.AT_HOSPITAL
        EscortProgress.AT_HOSPITAL -> EscortProgress.TO_HOME
        EscortProgress.TO_HOME -> EscortProgress.ARRIVED_HOME
        EscortProgress.ARRIVED_HOME -> null
    }

    // 동행 매니저가 버튼으로 동행 진행 단계를 변경한다. 위치로 단계가 자동 변경되지는 않는다.
    // 잠금, 단계 검증, 로그 기록, 위치 삭제 순서를 유지한다.
    @Transactional
    open fun updateProgress(applicationId: Long?, userId: Long?, progress: EscortProgress) {
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
        // 출발 버튼만 의뢰인과 가까워졌을 때(반경 100m, 두 위치 모두 최근) 누를 수 있다. 나머지 단계는 거리와 무관하다.
        if (progress == EscortProgress.DEPARTED) {
            val proximity = proximityOf(
                application.lat, application.lng, application.locationUpdatedAt,
                application.clientLat, application.clientLng, application.clientLocationUpdatedAt,
                Instant.now(),
            )
            if (!proximity.nearby) {
                throw InvalidException(37, "의뢰인과 가까워져야 출발할 수 있습니다.")
            }
        }

        val occurredAt = LocalDateTime.now()
        escortProgressLogRepository.save(
            EscortProgressLog(application, progress, occurredAt),
        )
        rideService.move(application.activePostId!!, progress)

        // 의뢰인이 최종 완료 확인하기 전이라도 귀가 완료 시 위치 정보를 삭제한다.
        if (progress == EscortProgress.ARRIVED_HOME) {
            applicationRepository.clearLocation(applicationId)
        }
        if (progress == EscortProgress.DEPARTED) {
            post.startProgress(occurredAt)
        }
        log.info("[동행 진행상태 변경] applicationId={}, progress={}, escortId={}", applicationId, progress, userId)

        when (progress) {
            EscortProgress.AT_HOSPITAL -> noticeService.send(
                application.escort.id,
                C_APPLICATION_PROGRESS_AT_HOSPITAL.of(application.post.hospitalName),
                U_APPLICATION_PROGRESS_AT_HOSPITAL.of(requireNotNull(applicationId) { "Application ID must not be null" }),
            )
            EscortProgress.ARRIVED_HOME -> noticeService.send(
                application.post.client.id,
                C_APPLICATION_PROGRESS_ARRIVED_HOME.of(application.post.hospitalName),
                U_APPLICATION_PROGRESS_ARRIVED_HOME.of(requireNotNull(applicationId) { "Application ID must not be null" }, requireNotNull(application.post.id) { "Post ID must not be null" }),
            )
            else -> {
                noticeService.send(
                    application.post.client.id,
                    C_APPLICATION_UPDATED_PROGRESS.of(progress.description),
                    U_APPLICATION_UPDATED_PROGRESS_CLIENT.of(requireNotNull(applicationId) { "Application ID must not be null" }, requireNotNull(application.post.id) { "Post ID must not be null" }),
                )
                noticeService.send(
                    application.escort.id,
                    C_APPLICATION_UPDATED_PROGRESS.of(progress.description),
                    U_APPLICATION_UPDATED_PROGRESS_ESCORT.of(requireNotNull(applicationId) { "Application ID must not be null" }),
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
     * 의뢰인 또는 동행 매니저의 위치를 저장한다. 사람마다 마지막 위치 1건만 DB 에 덮어쓴다.
     * 5초 단위 API이므로 조건부 UPDATE로 DB 컬럼만 갱신하고, 이력은 DB 에 쌓지 않고 하트비트 로그로만 남긴다.
     * 공고 락을 먼저 획득하여 귀가 완료와 위치 저장을 순차 처리한다.
     * 위치로 동행 진행 단계가 바뀌지는 않는다. 단계는 동행 매니저가 버튼으로만 바꾼다.
     */
    @Transactional
    open fun saveLocation(applicationId: Long?, userId: Long?, request: ApplicationLocationRequest) {
        val postId = applicationRepository.findPostIdById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val post = postRepository.findByIdWithLock(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        val isEscort = application.escort.id == userId
        val isClient = !isEscort && post.client.id == userId
        if (!isEscort && !isClient) {
            throw ForbiddenException(29, "해당 동행의 의뢰인 또는 동행 매니저만 실시간 위치를 보낼 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED) {
            throw InvalidException(33, "승인된 동행에서만 실시간 위치를 보낼 수 있습니다.")
        }
        if (post.postStatus !in LOCATION_SHARING_STATUSES) {
            throw InvalidException(34, "출발 전부터 귀가 완료 전까지만 실시간 위치를 보낼 수 있습니다.")
        }

        val now = Instant.now()
        val updated = if (isEscort) {
            applicationRepository.updateLocation(applicationId, request.lat, request.lng, request.accuracy, now)
        } else {
            applicationRepository.updateClientLocation(applicationId, request.lat, request.lng, request.accuracy, now)
        }
        // 위 확인 이후 저장하기 전에 귀가 완료·취소 등으로 동행이 끝난 경우 (조건부 UPDATE 가 0건)
        if (updated == 0) {
            throw InvalidException(34, "출발 전부터 귀가 완료 전까지만 실시간 위치를 보낼 수 있습니다.")
        }

        logLocationHeartbeat(application, isEscort, request, now)
    }

    /**
     * 위치 하트비트 로그. 좌표는 개인정보라 남기지 않고, 전송이 계속 오고 있다는 사실과 상대와의 거리만 남긴다.
     * (info: 한 줄 요약, debug: 상대 위치가 얼마나 오래됐는지 등 상세)
     */
    private fun logLocationHeartbeat(
        application: Application,
        senderIsEscort: Boolean,
        request: ApplicationLocationRequest,
        now: Instant,
    ) {
        // 보낸 사람의 위치는 방금 받은 값, 상대의 위치는 DB 에 있던 값을 쓴다.
        val proximity = if (senderIsEscort) {
            proximityOf(
                request.lat, request.lng, now,
                application.clientLat, application.clientLng, application.clientLocationUpdatedAt,
                now,
            )
        } else {
            proximityOf(
                application.lat, application.lng, application.locationUpdatedAt,
                request.lat, request.lng, now,
                now,
            )
        }
        log.info(
            "[위치 하트비트] applicationId={}, role={}, accuracy={}m, peerDistance={}m, nearby={}",
            application.id, if (senderIsEscort) "ESCORT" else "CLIENT", request.accuracy, proximity.distanceMeters, proximity.nearby,
        )
        if (log.isDebugEnabled) {
            val peerUpdatedAt = if (senderIsEscort) application.clientLocationUpdatedAt else application.locationUpdatedAt
            log.debug(
                "[위치 하트비트 상세] applicationId={}, role={}, peerUpdatedSecondsAgo={}",
                application.id, if (senderIsEscort) "ESCORT" else "CLIENT", peerUpdatedAt?.let { now.epochSecond - it.epochSecond },
            )
        }
    }

    private data class Proximity(val nearby: Boolean, val distanceMeters: Int?)

    /**
     * 두 사람이 가까운지 계산한다. 한쪽이라도 위치가 없거나 stale-seconds 보다 오래됐으면 가깝지 않은 것으로 본다.
     */
    private fun proximityOf(
        escortLat: BigDecimal?, escortLng: BigDecimal?, escortUpdatedAt: Instant?,
        clientLat: BigDecimal?, clientLng: BigDecimal?, clientUpdatedAt: Instant?,
        now: Instant,
    ): Proximity {
        if (escortLat == null || escortLng == null || escortUpdatedAt == null ||
            clientLat == null || clientLng == null || clientUpdatedAt == null
        ) {
            return Proximity(false, null)
        }
        val oldest = now.minusSeconds(staleSeconds)
        if (escortUpdatedAt.isBefore(oldest) || clientUpdatedAt.isBefore(oldest)) {
            return Proximity(false, null)
        }
        val distance = GeoUtils.distanceMeters(escortLat, escortLng, clientLat, clientLng)
        return Proximity(distance <= proximityRadiusMeters, distance.roundToInt())
    }

    /**
     * 동행 중(출발 후 ~ 귀가 완료 전) 해당 동행의 의뢰인과 동행 매니저만, 서로의 최근 위치와 가까움 여부를 조회한다.
     * 동행이 끝나면(귀가 완료·완료·취소) 아무도 조회할 수 없다.
     */
    @Transactional(readOnly = true)
    open fun getLocation(applicationId: Long?, userId: Long?): ApplicationLocationResponse {
        val application = applicationRepository.findById(requireNotNull(applicationId) { "The given id must not be null" })
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }
        val isEscort = application.escort.id == userId
        val isClient = !isEscort && application.post.client.id == userId
        if (!isEscort && !isClient) {
            throw ForbiddenException(30, "본인의 동행 건만 실시간 위치를 조회할 수 있습니다.")
        }
        if (application.status != ApplicationStatus.ACCEPTED ||
            application.post.postStatus !in LOCATION_SHARING_STATUSES ||
            escortProgressLogRepository.existsByApplicationAndProgress(application, EscortProgress.ARRIVED_HOME)
        ) {
            throw NotFoundException(31, "실시간 위치를 공유 중인 동행이 아닙니다.")
        }

        val proximity = proximityOf(
            application.lat, application.lng, application.locationUpdatedAt,
            application.clientLat, application.clientLng, application.clientLocationUpdatedAt,
            Instant.now(),
        )
        return ApplicationLocationResponse(
            escort = if (application.lat != null && application.lng != null) {
                LocationPoint(application.lat, application.lng, application.accuracy, application.locationUpdatedAt)
            } else {
                null
            },
            client = if (application.clientLat != null && application.clientLng != null) {
                LocationPoint(application.clientLat, application.clientLng, application.clientAccuracy, application.clientLocationUpdatedAt)
            } else {
                null
            },
            nearby = proximity.nearby,
            distanceMeters = proximity.distanceMeters,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApplicationService::class.java)

        // 출발 버튼을 누르려면 둘의 거리를 알아야 하므로, 출발 전(MATCHED)부터 귀가 완료 전까지 위치를 공유한다.
        private val LOCATION_SHARING_STATUSES = setOf(PostStatus.MATCHED, PostStatus.IN_PROGRESS)
    }
}
