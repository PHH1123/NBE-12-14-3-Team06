package com.back.nbe12142team06.domain.ride.service

import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.ride.dto.RideResponse
import com.back.nbe12142team06.domain.ride.dto.RideUpdateRequest
import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RideService(
    private val rideRepository: RideRepository,
    private val applicationRepository: ApplicationRepository
) {

    // 이동 수단 변경
    @Transactional
    fun updateRide(postId: Long, request: RideUpdateRequest): List<Ride> {
        val rides = findByPostId(postId)

        for (ride in rides) {

            val status = ride.rideStatus
            if (status == RideStatus.IN_PROGRESS ||
                status == RideStatus.COMPLETED
            ) {
                throw InvalidException(81, "이미 이동 중이거나 이동 완료이므로 수정이 불가능합니다.")
            }

            val select = when (ride.direction) {
                RideDirection.TO_HOSPITAL -> request.rideSelectToHospital!!
                RideDirection.TO_HOME -> request.rideSelectToHome!!
            }
            ride.updateSelect(select)
        }

        return rides
    }

    // 공고로 상세 정보 찾기
    fun findByPostId(postId: Long): List<Ride> =
        rideRepository.findAllByPostId(postId).ifEmpty { throw NotFoundException(81, "해당 공고의 이동 정보를 찾을 수 없습니다.") }

    // 공고로 목록 찾기
    fun getListByPostIdAndUserId(postId: Long, userId: Long): List<RideResponse> {
        val rides: List<Ride> = rideRepository.findAllByPostId(postId)
        var isValid = false

        // 의뢰인 검증
        for (ride in rides) {
            if (ride.validClient(userId)) {
                isValid = true
            }
        }

        // 동행 매니저 검증
        if (!isValid) {
            applicationRepository.findByPostIdAndEscortId(postId, userId)
                .ifEmpty { throw ForbiddenException(81, "이동 정보를 조회할 권한이 없습니다.") }
        }

        return rides.map { ride -> RideResponse.from(ride) }
    }

    // 상세 정보 찾기
    fun findById(userId: Long, rideId: Long): Ride =
        (rideRepository.findByIdOrNull(rideId)
            ?: throw NotFoundException(82, "이동 정보를 찾을 수 없습니다."))
            .also {
                if (!it.validClient(userId)) {
                    throw ForbiddenException(81, "이동 정보를 조회할 권한이 없습니다.")
                }
            }

    @Transactional
    fun move(postId: Long, progress: EscortProgress): List<Ride> {
        val rides: List<Ride> = rideRepository.findAllByPostId(postId)

        for (ride in rides) {
            if (ride.rideStatus == RideStatus.COMPLETED) continue
            ride.nextStatus(progress)
        }

        return rides
    }

    @Transactional
    fun createRide(post: Post, toHospital: RideSelect, toHome: RideSelect) {
        rideRepository.save(
            Ride(
                post = post,
                selected = toHospital,
            )
        )
        rideRepository.save(
            Ride(
                post = post,
                selected = toHome,
                direction = RideDirection.TO_HOME
            )
        )
    }
}
