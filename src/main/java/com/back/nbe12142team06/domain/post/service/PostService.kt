package com.back.nbe12142team06.domain.post.service

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus.ACCEPTED
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus.PENDING
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.post.dto.PostDto
import com.back.nbe12142team06.domain.post.dto.PostSearchConditionDto
import com.back.nbe12142team06.domain.post.dto.PostWriteRequest
import com.back.nbe12142team06.domain.post.dto.PostWriteResponse
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.dto.RideUpdateRequest
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.enums.UserStatus
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.EscortService
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Transactional
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val escortProgressLogRepository: EscortProgressLogRepository,
    private val applicationRepository: ApplicationRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val paymentService: PaymentService,
    private val rideService: RideService,
    private val escortService: EscortService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findAll(pageable: Pageable): Page<Post> {
        return postRepository.findAllWithClient(pageable)
    }

    // 내가 작성한 공고 목록 — 결제 상태와 무관하게 본인 소유 공고를 전부 보여준다(GET /api/v1/posts 와 달리 결제 DONE 필터 없음).
    fun findMyPosts(userId: Long, pageable: Pageable): Page<Post> {
        return postRepository.findAllByClientId(userId, pageable)
    }

    fun findById(postId: Long): Post {
        return postRepository.findByIdWithClient(postId)
            .orElseThrow { NotFoundException(11, "${postId}번 공고가 없습니다.") }
    }

    private fun getUser(userId: Long): User {
        return userRepository.findByIdOrNull(userId)
            ?: throw NotFoundException(1, "회원 정보를 찾을 수 없습니다.")
    }

    // 시간 검증
    private fun validateTime(
        recruitStartAt: LocalDateTime, recruitEndAt: LocalDateTime,
        escortStartAt: LocalDateTime, escortEndAt: LocalDateTime,
    ) {
        if (!LocalDateTime.now().isBefore(recruitStartAt)) {
            throw InvalidException(11, "모집 시작 시간은 현재 시간보다 이후여야 합니다.")
        }
        if (!recruitStartAt.isBefore(recruitEndAt)) {
            throw InvalidException(12, "모집 시작 시간은 모집 마감 시간보다 빨라야 합니다.")
        }
        if (!recruitEndAt.isBefore(escortStartAt)) {
            throw InvalidException(13, "모집 마감 시간은 동행 시작 시간보다 빨라야 합니다.")
        }
        if (!escortStartAt.isBefore(escortEndAt)) {
            throw InvalidException(14, "동행 시작 시간은 종료 시간보다 빨라야 합니다.")
        }
    }

    // 요청의 필수 값은 컨트롤러의 @Valid 검증을 통과해 모두 들어 있다 (그래서 아래에서 !! 를 쓴다)
    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun write(userId: Long, request: PostWriteRequest): PostWriteResponse {
        val user = getUser(userId)
        if (user.userStatus != UserStatus.ACTIVE) {
            throw ForbiddenException(31, "공고 등록 권한이 없습니다.")
        }
        if (user.role != Role.CLIENT && user.role != Role.ADMIN) {
            throw ForbiddenException(11, "공고 등록 권한이 없습니다. 의뢰인으로 로그인 해주세요.")
        }
        validateTime(
            request.recruitStartAt!!, request.recruitEndAt!!,
            request.escortStartAt!!, request.escortEndAt!!,
        )

        // 미결제 검증
        val isNotPaid = paymentService.validNotPaid(userId)
        if (!isNotPaid) {
            throw InvalidException(20, "미결제 공고가 있습니다.")
        }

        val post = Post(
            client = user,
            title = request.title!!,
            content = request.content!!,
            region = request.region!!,
            hospitalName = request.hospitalName!!,
            hospitalAddress = request.hospitalAddress!!,
            hospitalLat = request.hospitalLat!!,
            hospitalLng = request.hospitalLng!!,
            pickupAddress = request.pickupAddress!!,
            pickupLat = request.pickupLat!!,
            pickupLng = request.pickupLng!!,
            hourlyPay = request.hourlyPay,
            recruitStartAt = request.recruitStartAt,
            recruitEndAt = request.recruitEndAt,
            escortStartAt = request.escortStartAt,
            escortEndAt = request.escortEndAt,
            patientNote = request.patientNote,
            reportRequired = request.reportRequired,
        )

        val savedPost = postRepository.save(post)

        // 결제 데이터 생성
        val payment = paymentService.createPayment(savedPost)
        // 이동수단 데이터 생성
        rideService.createRide(savedPost, request.rideSelectToHospital!!, request.rideSelectToHome!!)

        log.info("[공고 등록] postId={}, clientId={}", savedPost.id, userId)
        return PostWriteResponse(savedPost, payment.id)
    }

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun modify(postId: Long, userId: Long, request: PostWriteRequest) {
        val user = getUser(userId)
        val post = postRepository.findByIdWithClient(postId)
            .orElseThrow { NotFoundException(1, "${postId}번 공고가 없습니다.") }

        if (user.role != Role.CLIENT && user.role != Role.ADMIN) {
            throw ForbiddenException(12, "공고 수정 권한이 없습니다. 의뢰인으로 로그인 해주세요.")
        }
        if (user.role == Role.CLIENT) {
            if (post.client.id != user.id) {
                throw ForbiddenException(13, "본인이 작성한 공고만 수정할 수 있습니다.")
            }
            if (!LocalDateTime.now().isBefore(post.recruitStartAt)) {
                throw InvalidException(15, "모집이 시작된 이후에는 공고를 수정할 수 없습니다. 모집 삭제 후 재등록해주세요.")
            }
            if (post.postStatus != PostStatus.OPEN) {
                throw InvalidException(16, "모집 중 상태에서만 수정이 가능합니다.")
            }
        }

        validateTime(
            request.recruitStartAt!!, request.recruitEndAt!!,
            request.escortStartAt!!, request.escortEndAt!!,
        )

        post.modify(
            request.title!!, request.content!!, request.region!!,
            request.hospitalName!!, request.hospitalAddress!!,
            request.hospitalLat!!, request.hospitalLng!!,
            request.pickupAddress!!, request.pickupLat!!, request.pickupLng!!,
            request.hourlyPay,
            request.recruitStartAt, request.recruitEndAt,
            request.escortStartAt, request.escortEndAt,
            request.patientNote, request.reportRequired,
        ) //더티체킹으로 자동 update 쿼리 생성

        // 이동 수단 변경
        rideService.updateRide(postId, RideUpdateRequest(request.rideSelectToHospital, request.rideSelectToHome))

        // 수정된 금액과 결제 금액 검증
        paymentService.validPayment(post)

        log.info("[공고 수정] postId={}, userId={}", postId, userId)
    }

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun delete(postId: Long, userId: Long) {
        val user = getUser(userId)
        val post = postRepository.findByIdWithLock(postId)
            ?: throw NotFoundException(11, "${postId}번 공고가 없습니다.")

        if (user.role != Role.CLIENT && user.role != Role.ADMIN) {
            throw ForbiddenException(14, "공고 삭제 권한이 없습니다.")
        }
        if (user.role == Role.CLIENT) {
            if (post.client.id != user.id) {
                throw ForbiddenException(15, "본인이 작성한 공고만 삭제할 수 있습니다.")
            }
            if (post.postStatus != PostStatus.OPEN && post.postStatus != PostStatus.EXPIRED) {
                throw InvalidException(17, "모집 중이거나 만료 상태에서만 삭제가 가능합니다.")
            }
        }
        // 결제 상태 변경 -> 스케줄러로 삭제된 공고에 대해 결제 취소할 예정
        paymentService.cancelPostAndPayment(postId)

        postRepository.deleteById(postId)
        // 공고의 지원(대기중, 승인)을 패널티 없이 취소
        cancelApplicationsWithoutPenalty(post)
        log.info("[공고 삭제] postId={}, userId={}", postId, userId)
    }

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun matchedCancel(postId: Long, userId: Long) {
        val user = getUser(userId)
        //Post post = findById(postId);
        val post = postRepository.findByIdWithLock(postId)
            ?: throw NotFoundException(11, "${postId}번 공고가 없습니다.")

        if (user.role != Role.CLIENT && user.role != Role.ADMIN) {
            throw ForbiddenException(16, "매칭 취소 권한이 없습니다.")
        }

        // 의뢰인만 소유자/상태 검증. 관리자는 무조건 통과.
        if (user.role == Role.CLIENT) {
            if (post.client.id != user.id) {
                throw ForbiddenException(17, "본인이 작성한 공고만 매칭을 취소할 수 있습니다.")
            }
            if (post.postStatus != PostStatus.MATCHED) {
                throw InvalidException(18, "매칭된 상태에서만 취소할 수 있습니다.")
            }
        }

        post.matchedCancel()

        // 공고의 지원(대기중, 승인)을 패널티 없이 취소
        cancelApplicationsWithoutPenalty(post)
        log.info("[공고 매칭 취소] postId={}, userId={}", postId, userId)
    }

    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun expireOverduePosts() {
        val targets = postRepository.findAllByPostStatusAndRecruitEndAtBefore(
            PostStatus.OPEN, LocalDateTime.now(),
        )

        targets.forEach { it.expire() }
        // 변경 감지(더티체킹)로 트랜잭션 끝날 때 자동으로 UPDATE 쿼리 나감

        // 1분마다 도는 스케줄러라서 처리한 공고가 있을 때만 남긴다(안 그러면 하루 1,440줄이 쌓임)
        if (targets.isNotEmpty()) {
            log.info("[공고 만료 처리] {}건, postIds={}", targets.size, targets.map { it.id })
        }
    }

    fun search(condition: PostSearchConditionDto, pageable: Pageable): Page<Post> {
        return postRepository.search(
            condition.keyword, condition.region,
            condition.dateFrom, condition.dateTo,
            condition.minPay, condition.maxPay,
            condition.openOnly,
            pageable,
        )
    }

    //escortComplete:동행완료가 되면, 에스코트 시작,종료 update되게
    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun escortComplete(postId: Long, userId: Long) {
        val user = getUser(userId)
        // 실시간 위치 저장·진행 단계 변경과 같은 Post 락으로 종료 처리를 직렬화한다.
        val post = postRepository.findByIdWithLock(postId)
            ?: throw NotFoundException(11, "${postId}번 공고가 없습니다.")

        if (user.role != Role.CLIENT && user.role != Role.ADMIN) {
            throw ForbiddenException(18, "공고 동행완료 처리 권한이 없습니다.")
        }

        // 의뢰인만 소유자/상태 검증. 관리자는 무조건 통과.
        if (user.role == Role.CLIENT) {
            if (post.client.id != user.id) {
                throw ForbiddenException(19, "본인이 작성한 공고만 동행완료 처리할 수 있습니다.")
            }
            if (post.postStatus != PostStatus.IN_PROGRESS) {
                throw InvalidException(19, "동행진행중 상태에서만 동행완료 처리할 수 있습니다.")
            }
        }
        // 이 공고의 매칭된(승인된) 지원 조회
        val application = applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.ACCEPTED)
            .firstOrNull()
            ?: throw NotFoundException(21, "매칭된 지원 내역이 없습니다.")

        // 출발(DEPARTED) 시각 조회
        val departedAt = escortProgressLogRepository
            .findByApplicationAndProgress(application, EscortProgress.DEPARTED)
            .map { it.occurredAt }
            .orElseThrow { NotFoundException(22, "동행 출발 기록이 없습니다.") }
            ?: throw NotFoundException(22, "동행 출발 기록이 없습니다.")

        // 귀가완료(ARRIVED_HOME) 시각 조회
        val arrivedAt = escortProgressLogRepository
            .findByApplicationAndProgress(application, EscortProgress.ARRIVED_HOME)
            .map { it.occurredAt }
            .orElseThrow { NotFoundException(23, "귀가완료 기록이 없습니다.") }
            ?: throw NotFoundException(23, "귀가완료 기록이 없습니다.")

        // 동행 매니저 프로필 존재 확인 — 외부 결제 API(validPayment)보다 먼저 검증한다.
        // validPayment 는 Toss 부분 취소를 호출하므로, 그 뒤에 실패하면 환불은 되돌릴 수 없다.
        val escortId = application.escort.id
        if (!escortProfileRepository.existsById(escortId)) {
            throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")
        }

        post.startProgress(departedAt)   // escortStartAt 실제값 반영
        post.complete(arrivedAt)       // escortEndAt 실제값 반영 + 상태 COMPLETED
        // 정상 경로에서는 ARRIVED_HOME 때 삭제되지만, 완료 처리에서도 잔여 위치를 정리한다.
        applicationRepository.clearLocationsByPostId(postId)

        // 재결제 로직
        paymentService.validPayment(post, application, post.escortEndAt.plusDays(1).toLocalDate())

        // 동행 완료 건수 증가 — clearAutomatically 때문에 반드시 마지막에 호출
        val updated = escortProfileRepository.increaseCompletedCount(escortId)
        if (updated == 0) {
            throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")
        }
        val escortProfile = escortProfileRepository.findByIdOrNull(escortId)
            ?: throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")
        val refreshed = escortService.refreshGrade(escortProfile)
        if (refreshed) {
            log.info("[등급 변경 완료] postId={}, escortId={} ", postId, escortId)
        }
        log.info("[동행 완료] postId={}, escortId={}, userId={}", postId, escortId, userId)
    }

    /**
     * 회원 탈퇴·정지 시, 그 의뢰인의 모집 중(OPEN)·매칭된(MATCHED) 공고를 모두 취소한다.
     * 호출하는 쪽(UserService)에서 회원을 탈퇴 처리(withdraw)하기 전에 불러야 한다. (탈퇴하면 공고의 의뢰인을 불러올 수 없다)
     */
    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun cancelAllByClient(clientId: Long) {
        val posts = postRepository.findAllByClientIdAndPostStatusIn(
            clientId, listOf(PostStatus.OPEN, PostStatus.MATCHED),
        )

        // 공고마다 cancel 을 태워서 잠금과 상태 검증을 그대로 쓴다.
        for (post in posts) {
            try {
                cancel(post.id!!, clientId)
            } catch (e: InvalidException) {
                // 목록을 읽은 뒤 동행이 시작되는 등 공고 상태가 바뀐 경우.
                // 일부 공고만 취소된 채로 끝나지 않도록 예외를 던져 호출한 쪽의 트랜잭션(탈퇴·정지)을 통째로 롤백한다.
                log.warn("[회원 탈퇴/정지로 공고 취소 실패] clientId={}, postId={}, 사유={}", clientId, post.id, e.message)
                throw InvalidException(36, "공고 상태가 변경되어 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.")
            }
            paymentService.cancel(post)
        }

        if (posts.isNotEmpty()) {
            log.info("[회원 탈퇴/정지로 공고 취소] clientId={}, {}건", clientId, posts.size)
        }
    }

    //공고 취소(OPEN,MATCHED상태)
    @Transactional
    @CacheEvict(cacheNames = ["postList"], allEntries = true)
    fun cancel(postId: Long, userId: Long) {
        getUser(userId)

        val post = postRepository.findByIdWithLock(postId)
            ?: throw NotFoundException(11, "${postId}번 공고가 없습니다.")

        // 모집 중(OPEN)이거나 매칭된(MATCHED) 공고만 취소할 수 있다.
        if (post.postStatus != PostStatus.OPEN && post.postStatus != PostStatus.MATCHED) {
            throw InvalidException(35, "공고모집중 이거나 매칭된 상태에서만 취소할 수 있습니다.")
        }

        post.cancel()
        // 공고의 지원(대기중, 승인)을 패널티 없이 취소
        cancelApplicationsWithoutPenalty(post)
        log.info("[공고 취소] postId={}, userId={}", postId, userId)
    }

    fun hasInProgressPost(userId: Long): Boolean {
        return postRepository.existsByClientIdAndPostStatus(userId, PostStatus.IN_PROGRESS)
    }

    // 공고가 취소·삭제될 때, 그 공고의 지원(대기중, 승인)을 패널티 없이 취소한다.
    // 같은 트랜잭션 안에서 가져온 지원이라 변경 감지로 저장된다.
    private fun cancelApplicationsWithoutPenalty(post: Post) {
        // 공고 취소·매칭 취소·삭제 때 남은 위치정보가 있다면 함께 삭제한다.
        applicationRepository.clearLocationsByPostId(post.id)
        val acceptedApplications = applicationRepository.findAllByPostAndStatus(post, ACCEPTED)
        val pendingApplications = applicationRepository.findAllByPostAndStatus(post, PENDING)

        for (acceptedApplication in acceptedApplications) {
            acceptedApplication.cancelWithoutPenalty()
        }
        for (pendingApplication in pendingApplications) {
            pendingApplication.cancelWithoutPenalty()
        }
    }

    @Transactional
    fun canViewSensitive(post: Post, userId: Long): Boolean {
        // 작성자
        if (post.client.id == userId) {
            return true
        }

        // 관리자
        val user = getUser(userId)
        if (user.role == Role.ADMIN) {
            return true
        }

        // 이 공고에 승인된 동행인
        return applicationRepository.existsByPostIdAndEscortIdAndStatus(
            post.id, userId, ApplicationStatus.ACCEPTED,
        )
    }

    @Cacheable(cacheNames = ["postList"], key = "#condition + '-' + #pageable ")
    fun searchDtos(condition: PostSearchConditionDto, pageable: Pageable): Page<PostDto> {
        return search(condition, pageable).map { PostDto(it, false) }
    }
}
