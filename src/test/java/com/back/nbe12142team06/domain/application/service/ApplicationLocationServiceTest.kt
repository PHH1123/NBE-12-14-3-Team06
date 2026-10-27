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
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.eq
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
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
        private const val CLIENT_ID = 40L
        private const val STRANGER_ID = 99L
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
        `when`(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(Optional.of(POST_ID))
        `when`(postRepository.findByIdWithLock(POST_ID)).thenReturn(Optional.of(post))
        `when`(post.postStatus).thenReturn(PostStatus.IN_PROGRESS)
    }

    // 동행 매니저가 아닌 사람이 보낼 때는 공고의 의뢰인인지 확인하므로 post.client 도 필요하다.
    private fun prepareLocationWriteByNonEscort() {
        prepareLocationWrite()
        `when`(post.client).thenReturn(client)
        `when`(client.id).thenReturn(CLIENT_ID)
    }

    private fun prepareLocationRead() {
        prepareAcceptedApplication()
        `when`(application.post).thenReturn(post)
        `when`(post.postStatus).thenReturn(PostStatus.IN_PROGRESS)
    }

    private fun prepareLocationReadByClient() {
        prepareLocationRead()
        `when`(post.client).thenReturn(client)
        `when`(client.id).thenReturn(CLIENT_ID)
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
        verify(applicationRepository, never()).updateClientLocation(any(), any(), any(), any(), any())
    }

    @Test
    fun locationIsSavedBeforeDeparture() {
        // 출발 버튼은 둘이 가까워져야 활성화되므로, 출발 전(공고 MATCHED)에도 위치를 보낼 수 있다.
        prepareLocationWrite()
        `when`(post.postStatus).thenReturn(PostStatus.MATCHED)
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), BigDecimal.TEN)
        `when`(applicationRepository.updateLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))).thenReturn(1)

        applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request)

        verify(applicationRepository).updateLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))
    }

    @Test
    fun locationIsRejectedWhenPostIsAlreadyCompleted() {
        prepareLocationWrite()
        `when`(post.postStatus).thenReturn(PostStatus.COMPLETED)
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), BigDecimal.TEN)

        val ex = assertThrows(InvalidException::class.java) {
            applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request)
        }

        assertTrue(ex.message!!.contains("출발 전부터 귀가 완료 전까지"))
        verify(applicationRepository, never()).updateLocation(any(), any(), any(), any(), any())
    }

    @Test
    fun clientLocationIsSavedInClientColumnsWhileEscortIsInProgress() {
        prepareLocationWriteByNonEscort()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), BigDecimal.TEN)
        `when`(applicationRepository.updateClientLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))).thenReturn(1)

        applicationService.saveLocation(APPLICATION_ID, CLIENT_ID, request)

        verify(applicationRepository).updateClientLocation(eq(APPLICATION_ID), eq(request.lat), eq(request.lng),
            eq(request.accuracy), any(Instant::class.java))
        verify(applicationRepository, never()).updateLocation(any(), any(), any(), any(), any())
    }

    @Test
    fun locationCannotBeSentByAnOutsider() {
        prepareLocationWriteByNonEscort()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        val request = ApplicationLocationRequest(BigDecimal("37.1234567"), BigDecimal("127.1234567"), null)

        assertThrows(ForbiddenException::class.java) {
            applicationService.saveLocation(APPLICATION_ID, STRANGER_ID, request)
        }
        verify(applicationRepository, never()).updateLocation(any(), any(), any(), any(), any())
        verify(applicationRepository, never()).updateClientLocation(any(), any(), any(), any(), any())
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

        assertEquals(lat, response.escort?.lat)
        assertEquals(lng, response.escort?.lng)
        assertNull(response.client)           // 의뢰인은 아직 위치를 보내지 않았다
        assertFalse(response.nearby)
        assertNull(response.distanceMeters)
    }

    @Test
    fun clientCanReadBothLocationsAndTheyAreNearbyWhenWithinRadius() {
        prepareLocationReadByClient()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        ReflectionTestUtils.setField(applicationService, "proximityRadiusMeters", 100.0)
        ReflectionTestUtils.setField(applicationService, "staleSeconds", 30L)
        val now = Instant.now()
        // 위도 0.0005 차이는 약 55m
        `when`(application.lat).thenReturn(BigDecimal("37.5665000"))
        `when`(application.lng).thenReturn(BigDecimal("126.9780000"))
        `when`(application.locationUpdatedAt).thenReturn(now)
        `when`(application.clientLat).thenReturn(BigDecimal("37.5670000"))
        `when`(application.clientLng).thenReturn(BigDecimal("126.9780000"))
        `when`(application.clientLocationUpdatedAt).thenReturn(now)

        val response = applicationService.getLocation(APPLICATION_ID, CLIENT_ID)

        assertNotNull(response.escort)
        assertNotNull(response.client)
        assertTrue(response.nearby)
        assertTrue(response.distanceMeters!! in 40..70)
    }

    @Test
    fun locationIsNotNearbyWhenOneSideIsStale() {
        prepareLocationRead()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))
        ReflectionTestUtils.setField(applicationService, "proximityRadiusMeters", 100.0)
        ReflectionTestUtils.setField(applicationService, "staleSeconds", 30L)
        `when`(application.lat).thenReturn(BigDecimal("37.5665000"))
        `when`(application.lng).thenReturn(BigDecimal("126.9780000"))
        `when`(application.locationUpdatedAt).thenReturn(Instant.now())
        `when`(application.clientLat).thenReturn(BigDecimal("37.5666000"))
        `when`(application.clientLng).thenReturn(BigDecimal("126.9780000"))
        `when`(application.clientLocationUpdatedAt).thenReturn(Instant.now().minusSeconds(120)) // 오래된 위치

        val response = applicationService.getLocation(APPLICATION_ID, ESCORT_ID)

        assertFalse(response.nearby)
        assertNull(response.distanceMeters)
    }

    @Test
    fun locationCannotBeReadByAnOutsider() {
        prepareLocationReadByClient()
        `when`(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application))

        assertThrows(ForbiddenException::class.java) {
            applicationService.getLocation(APPLICATION_ID, STRANGER_ID)
        }
    }

    @Test
    fun arrivedHomeClearsAllLocationColumnsImmediately() {
        `when`(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(Optional.of(POST_ID))
        `when`(postRepository.findByIdWithLock(POST_ID)).thenReturn(Optional.of(post))
        `when`(applicationRepository.findByIdWithLock(APPLICATION_ID)).thenReturn(Optional.of(application))
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
