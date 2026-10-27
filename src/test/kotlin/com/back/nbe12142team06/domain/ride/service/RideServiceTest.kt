package com.back.nbe12142team06.domain.ride.service

import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import com.back.nbe12142team06.domain.ride.repository.RideRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RideServiceTest {

    @field:Autowired private lateinit var rideService: RideService

    @field:MockitoSpyBean private lateinit var rideRepository: RideRepository

    private fun createRide(id: Long, direction: RideDirection, status: RideStatus): Ride =
        Ride(id, direction, status, RideSelect.TAXI, Post())

    // 공고 하나에 병원 이동·귀가 이동이 한 쌍으로 붙어 있는 상태를 만든다
    private fun stubRides(toHospital: RideStatus, toHome: RideStatus): Pair<Ride, Ride> {
        val rideToHospital = createRide(1L, RideDirection.TO_HOSPITAL, toHospital)
        val rideToHome = createRide(2L, RideDirection.TO_HOME, toHome)

        doReturn(listOf(rideToHospital, rideToHome)).whenever(rideRepository).findAllByPostId(any())

        return rideToHospital to rideToHome
    }

    private fun List<Ride>.of(direction: RideDirection): Ride = first { it.direction == direction }

    @Test
    @DisplayName("[RideService] ACCEPTED → IN_PROGRESS, 병원 이동")
    fun hospitalRideStatusAcceptedToInProgress() {
        stubRides(RideStatus.ACCEPTED, RideStatus.ACCEPTED)

        val rides = rideService.move(1L, EscortProgress.TO_HOSPITAL)

        assertThat(rides.of(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.IN_PROGRESS)
    }

    @Test
    @DisplayName("[RideService] ACCEPTED → IN_PROGRESS, 집 이동")
    fun homeRideStatusAcceptedToInProgress() {
        stubRides(RideStatus.COMPLETED, RideStatus.ACCEPTED)

        val rides = rideService.move(1L, EscortProgress.TO_HOME)

        assertThat(rides.of(RideDirection.TO_HOME).rideStatus).isEqualTo(RideStatus.IN_PROGRESS)
    }

    @Test
    @DisplayName("[RideService] IN_PROGRESS → COMPLETED, 병원 이동")
    fun hospitalRideStatusInProgressToCompleted() {
        stubRides(RideStatus.IN_PROGRESS, RideStatus.ACCEPTED)

        val rides = rideService.move(1L, EscortProgress.AT_HOSPITAL)

        assertThat(rides.of(RideDirection.TO_HOSPITAL).rideStatus).isEqualTo(RideStatus.COMPLETED)
    }

    @Test
    @DisplayName("[RideService] IN_PROGRESS → COMPLETED, 집 이동")
    fun homeRideStatusInProgressToCompleted() {
        stubRides(RideStatus.COMPLETED, RideStatus.IN_PROGRESS)

        val rides = rideService.move(1L, EscortProgress.ARRIVED_HOME)

        assertThat(rides.of(RideDirection.TO_HOME).rideStatus).isEqualTo(RideStatus.COMPLETED)
    }

    @Test
    @DisplayName("[RideService] COMPLETED에서 move")
    fun rideStatusCompletedMove() {
        stubRides(RideStatus.COMPLETED, RideStatus.COMPLETED)

        // 이동 완료 건은 건너뛰므로 상태 전이 예외 없이 그대로 유지된다
        val rides = rideService.move(1L, EscortProgress.ARRIVED_HOME)

        assertThat(rides.map { it.rideStatus })
            .containsExactly(RideStatus.COMPLETED, RideStatus.COMPLETED)
    }
}
