package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.repository.ClientProfileRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.EscortService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ApplicationInProgressServiceTest {

    @field:Mock
    private lateinit var applicationRepository: ApplicationRepository

    @field:Mock
    private lateinit var postRepository: PostRepository
    @field:Mock
    private lateinit var userRepository: UserRepository
    @field:Mock
    private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Mock
    private lateinit var escortProgressLogRepository: EscortProgressLogRepository
    @field:Mock
    private lateinit var noShowPenaltyService: NoShowPenaltyService
    @field:Mock
    private lateinit var clientProfileRepository: ClientProfileRepository
    @field:Mock
    private lateinit var paymentRepository: PaymentRepository
    @field:Mock
    private lateinit var paymentService: PaymentService
    @field:Mock
    private lateinit var rideService: RideService
    @field:Mock
    private lateinit var escortService: EscortService
    @field:Mock
    private lateinit var noticeService: NoticeService
    @field:InjectMocks
    private lateinit var applicationService: ApplicationService

    @Test
    @DisplayName("진행 중인 동행이 있으면 true 반환")
    fun hasInProgressEscort_true() {
        val escortId = 1L

        `when`(
            applicationRepository.existsInProgressByEscortId(
                escortId,
                ApplicationStatus.ACCEPTED,
                PostStatus.IN_PROGRESS,
            )
        ).thenReturn(true)

        val result = applicationService.hasInProgressEscort(escortId)

        assertTrue(result)
    }

    @Test
    @DisplayName("진행 중인 동행이 없으면 false 반환")
    fun hasInProgressEscort_false() {
        val escortId = 1L

        `when`(
            applicationRepository.existsInProgressByEscortId(
                escortId,
                ApplicationStatus.ACCEPTED,
                PostStatus.IN_PROGRESS,
            )
        ).thenReturn(false)

        val result = applicationService.hasInProgressEscort(escortId)

        assertFalse(result)
    }
}
