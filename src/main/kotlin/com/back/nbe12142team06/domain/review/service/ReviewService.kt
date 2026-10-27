package com.back.nbe12142team06.domain.review.service

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.notification.entity.NotificationContent.C_REVIEW_WRITE
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl.U_REVIEW_WRITE
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.review.dto.ReviewDto
import com.back.nbe12142team06.domain.review.dto.ReviewWriteRequest
import com.back.nbe12142team06.domain.review.entity.Review
import com.back.nbe12142team06.domain.review.entity.ReviewTag
import com.back.nbe12142team06.domain.review.repository.ReviewRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.global.exception.DuplicatedException
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.HashSet

@Service
@Transactional(readOnly = true) // 기본은 읽기 전용 트랜잭션으로 설정
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val applicationRepository: ApplicationRepository,
    private val userRepository: UserRepository,
    private val escortProfileRepository: EscortProfileRepository,
    private val noticeService: NoticeService,
) {

    private val log = LoggerFactory.getLogger(ReviewService::class.java)

    @Transactional // 쓰기 작업을 수행하는 메서드에는 readOnly 해제
    fun write(applicationId: Long, actorId: Long, request: ReviewWriteRequest): Review {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException(24, "지원을 찾을 수 없습니다.") }

        // 리뷰는 해당 공고를 등록한 의뢰인만 작성 가능
        if (!application.post.client.id.equals(actorId)) {
            throw ForbiddenException(61, "본인이 의뢰한 동행 건에만 리뷰를 작성할 수 있습니다.")
        }

        // 매칭이 확정된 동행 건에만 리뷰 작성 가능
        if (application.status != ApplicationStatus.ACCEPTED ||
            application.post.postStatus != PostStatus.COMPLETED
        ) {
            throw InvalidException(61, "매칭이 확정된 동행 건에만 리뷰를 작성할 수 있습니다.")
        }

        if (reviewRepository.existsByApplicationId(applicationId)) {
            throw DuplicatedException(61, "이미 리뷰가 작성된 동행 건입니다.")
        }

        // 태그 미선택 시 null 이 들어오므로 빈 컬렉션으로 대체
        val tags: Set<ReviewTag> = request.tags ?: HashSet()

        val review = reviewRepository.save(
            Review(
                application = application,
                rating = request.rating!!,
                tags = tags,
                content = request.content,
            )
        )

        // 동행 매니저 프로필 평점 반영.
        // 엔티티를 읽어 더티체킹으로 갱신하면 같은 동행인에게 리뷰가 동시에 여러 건
        // 작성될 때 lost update 가 발생하므로, DB 에서 원자적으로 증가시키는 벌크 UPDATE 를 쓴다.
        val escortId = application.escort.id
        val updated = escortProfileRepository.addRating(escortId, request.rating)
        if (updated == 0) {
            throw NotFoundException(3, "동행 매니저 프로필이 존재하지 않습니다.")
        }

        log.info("[리뷰 작성] applicationId={}, clientId={}, rating={}", applicationId, actorId, request.rating)

        // 알림
        noticeService.send(
            application.escort.id,
            C_REVIEW_WRITE.of(application.post.title),
            U_REVIEW_WRITE.of()
        )

        return review
    }

    /**
     * 특정 동행인이 받은 리뷰 목록 조회 (클래스의 readOnly 적용)
     *
     * DTO 변환을 컨트롤러가 아니라 여기서 한다.
     * 엔티티를 그대로 반환하면 트랜잭션이 끝난 뒤 JSON 으로 바꿀 때 태그를 읽게 되어
     * LazyInitializationException 이 발생한다. (open-in-view: false 라 트랜잭션 종료와 함께 세션이 닫힌다)
     */
    fun findAllByEscortId(escortId: Long): List<ReviewDto> {
        // 리뷰가 0건인 것은 정상이므로, 회원 존재 여부만 확인
        if (!userRepository.existsById(escortId)) {
            throw NotFoundException(1, "회원 정보를 찾을 수 없습니다.")
        }

        return reviewRepository.findAllByEscortIdWithApplication(escortId).map(::ReviewDto)
    }
}
