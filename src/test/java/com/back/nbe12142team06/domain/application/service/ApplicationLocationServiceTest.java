package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.dto.ApplicationLocationRequest;
import com.back.nbe12142team06.domain.application.dto.ApplicationLocationResponse;
import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.entity.EscortProgressLog;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.enums.EscortProgress;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository;
import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.ride.service.RideService;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationLocationServiceTest {

    private static final Long APPLICATION_ID = 10L;
    private static final Long POST_ID = 20L;
    private static final Long ESCORT_ID = 30L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private EscortProgressLogRepository escortProgressLogRepository;
    @Mock private PostRepository postRepository;
    @Mock private RideService rideService;
    @Mock private NoticeService noticeService;

    @InjectMocks private ApplicationService applicationService;

    private final Application application = mock(Application.class);
    private final Post post = mock(Post.class);
    private final User escort = mock(User.class);

    private void prepareAcceptedApplication() {
        when(application.getEscort()).thenReturn(escort);
        when(escort.getId()).thenReturn(ESCORT_ID);
        when(application.getStatus()).thenReturn(ApplicationStatus.ACCEPTED);
    }

    private void prepareLocationWrite() {
        prepareAcceptedApplication();
        when(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(Optional.of(POST_ID));
        when(postRepository.findByIdWithLock(POST_ID)).thenReturn(Optional.of(post));
        when(post.getPostStatus()).thenReturn(PostStatus.IN_PROGRESS);
    }

    private void prepareLocationRead() {
        prepareAcceptedApplication();
        when(application.getPost()).thenReturn(post);
        User client = mock(User.class);
        when(post.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(40L);
        when(post.getPostStatus()).thenReturn(PostStatus.IN_PROGRESS);
    }

    @Test
    void locationIsSavedWhileEscortIsInProgress() {
        prepareLocationWrite();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        ApplicationLocationRequest request = new ApplicationLocationRequest(
                new BigDecimal("37.1234567"), new BigDecimal("127.1234567"), BigDecimal.TEN);
        when(applicationRepository.updateLocation(eq(APPLICATION_ID), eq(request.lat()), eq(request.lng()),
                eq(request.accuracy()), any(Instant.class))).thenReturn(1);

        applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request);

        verify(applicationRepository).updateLocation(eq(APPLICATION_ID), eq(request.lat()), eq(request.lng()),
                eq(request.accuracy()), any(Instant.class));
    }

    @Test
    void locationUpdateIsRejectedWhenConditionalUpdateFails() {
        prepareLocationWrite();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        ApplicationLocationRequest request = new ApplicationLocationRequest(
                new BigDecimal("37.1234567"), new BigDecimal("127.1234567"), null);
        // 귀가 완료나 공고 종료 후에는 Repository의 조건부 UPDATE가 0을 반환해야 한다.
        assertThrows(InvalidException.class,
                () -> applicationService.saveLocation(APPLICATION_ID, ESCORT_ID, request));
    }

    @Test
    void locationIsNotReturnedAfterArrivedHomeEvenIfPostIsStillInProgress() {
        prepareLocationRead();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(escortProgressLogRepository.existsByApplicationAndProgress(
                application, EscortProgress.ARRIVED_HOME)).thenReturn(true);

        assertThrows(NotFoundException.class,
                () -> applicationService.getLocation(APPLICATION_ID, ESCORT_ID));
    }

    @Test
    void locationIsReturnedBeforeArrivedHome() {
        prepareLocationRead();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        BigDecimal lat = new BigDecimal("37.1234567");
        BigDecimal lng = new BigDecimal("127.1234567");
        when(application.getLat()).thenReturn(lat);
        when(application.getLng()).thenReturn(lng);

        ApplicationLocationResponse response = applicationService.getLocation(APPLICATION_ID, ESCORT_ID);

        assertEquals(lat, response.lat());
        assertEquals(lng, response.lng());
    }

    @Test
    void arrivedHomeClearsAllLocationColumnsImmediately() {
        when(applicationRepository.findPostIdById(APPLICATION_ID)).thenReturn(Optional.of(POST_ID));
        when(postRepository.findByIdWithLock(POST_ID)).thenReturn(Optional.of(post));
        when(applicationRepository.findByIdWithLock(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(application.getEscort()).thenReturn(escort);
        when(escort.getId()).thenReturn(ESCORT_ID);
        when(application.getStatus()).thenReturn(ApplicationStatus.ACCEPTED);
        when(application.getActivePostId()).thenReturn(POST_ID);
        when(application.getPost()).thenReturn(post);
        when(escortProgressLogRepository.findTopByApplicationOrderByOccurredAtDescIdDesc(application))
                .thenReturn(Optional.of(EscortProgressLog.builder()
                        .application(application)
                        .progress(EscortProgress.TO_HOME)
                        .occurredAt(LocalDateTime.now().minusMinutes(10))
                        .build()));

        applicationService.updateProgress(APPLICATION_ID, ESCORT_ID, EscortProgress.ARRIVED_HOME);

        verify(applicationRepository).clearLocation(APPLICATION_ID);
        verify(rideService).move(POST_ID, EscortProgress.ARRIVED_HOME);
    }
}
