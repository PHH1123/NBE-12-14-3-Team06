package com.back.nbe12142team06.domain.application.service

import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest
import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.enums.EscortProgress
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.repository.ClientProfileRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.EscortService
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.eq
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ApplicationLocationServiceTest {

    companion object {
        private const val APPLICATION_ID = 10L
        private const val POST_ID = 20L
        private const val ESCORT_ID = 30L
    }

    @field:Mock private lateinit var applicationRepository: ApplicationRepository
    @field:Mock private lateinit var escortProgressLogRepository: EscortProgressLogRepository
    @field:Mock private lateinit var postRepository: PostRepository
    @field:Mock private lateinit var rideService: RideService
    @field:Mock private lateinit var noticeService: NoticeService
    @field:Mock private lateinit var userRepository: UserRepository
    @field:Mock private lateinit var escortProfileRepository: EscortProfileRepository
    @field:Mock private lateinit var noShowPenaltyService: NoShowPenaltyService
    @field:Mock private lateinit var clientProfileRepository: ClientProfileRepository
    @field:Mock private lateinit var paymentRepository: PaymentRepository
    @field:Mock private lateinit var paymentService: PaymentService
    @field:Mock private lateinit var escortService: EscortService
    @field:InjectMocks private lateinit var applicationService: ApplicationService

    private val application = mock(Application::class.java)
    private val post = mock(Post::class.java)
    private val escort = mock(User::class.java)
    private val client = mock(User::class.java)

    private fun prepareAcceptedApplication() {
        `when`(application.escort).thenReturn(escort)
        `when`(escort.id).thenReturn(ESCORT_ID)
        `when`(application.status).thenReturn(ApplicationStatus.ACCEPTED)
    }

    private fun prepareLocationWrite() {
        prepareAcceptedApplication()
        `when`(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(POST_ID)
        `when`(postRepository.findByIdWithLock(POST_ID)).thenReturn(post)
        `when`(post.postStatus).thenReturn(PostStatus.IN_PROGRESS)
    }

    private fun prepareLocationRead() {
        prepareAcceptedApplication()
        `when`(application.post).thenReturn(post)
        val client = mock(User::class.java)
        `when`(post.client).thenReturn(client)
        `when`(client.id).thenReturn(40L)
        `when`(post.postStatus).thenReturn(PostStatus.IN_PROGRESS)
    }

    @Test
    fun locationIsSavedWhileEscortIsInProgress() {
        prepareLocationWrite()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), BigDecimal.TEN)
        `when`(applicationRepository.updateLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))).thenReturn(1)

        applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request)

        verify(applicationRepository).updateLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))
    }

    @Test
    fun locationUpdateIsRejectedWhenConditionalUpdateFails() {
        prepareLocationWrite()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), null)
        // 귀가 완료나 공고 종료 후에는 Repository의 조건부 UPDATE가 0을 반환해야 한다.
        assertThrows(InvalidException::class.java) {
            applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request)
        }
    }

    @Test
    fun locationIsNotReturnedAfterArrivedHomeEvenIfPostIsStillInProgress() {
        prepareLocationRead()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        `when`(escortProgressLogRepository.existsByApplicationAndProgress(application, EscortProgress.ARRIVED_HOME))
            .thenReturn(true)

        assertThrows(NotFoundException::class.java) {
            applicationService.getLocation(APPLICATION_ID, ESCORT_ID)
        }
    }

    @Test
    fun locationIsReturnedBeforeArrivedHome() {
        prepareLocationRead()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val lat = BigDecimal("37.1234567")
        val lng = BigDecimal("127.1234567")
        `when`(application.lat).thenReturn(lat)
        `when`(application.lng).thenReturn(lng)

        val response = applicationService.getLocation(APPLICATION_ID, ESCORT_ID)

        assertEquals(lat, response.lat)
        assertEquals(lng, response.lng)
    }

    @Test
    fun arrivedHomeClearsAllLocationColumnsImmediately() {
        `when`(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(POST_ID)
        `when`(postRepository.findByIdWithLock(POST_ID)).thenReturn(post)
        `when`(applicationRepository.findByIdWithLock(APPLICATION_ID)).thenReturn(application)
        `when`(application.escort).thenReturn(escort)
        `when`(escort.id).thenReturn(ESCORT_ID)
        `when`(application.status).thenReturn(ApplicationStatus.ACCEPTED)
        `when`(application.activePostId).thenReturn(POST_ID)
        `when`(application.post).thenReturn(post)
        `when`(escortProgressLogRepository.findTopByApplicationOrderByOccurredAtDescIdDesc(application))
            .thenReturn(Optional.of(EscortProgressLog(
                application = application,
                progress = EscortProgress.TO_HOME,
                occurredAt = LocalDateTime.now().minusMinutes(10),
            )))
        `when`(application.post.client).thenReturn(client)

        applicationService.updateProgress(APPLICATION_ID, ESCORT_ID, EscortProgress.ARRIVED_HOME)

        verify(applicationRepository).clearLocation(APPLICATION_ID)
        verify(rideService).move(POST_ID, EscortProgress.ARRIVED_HOME)
    }
}
