
package com.back.nbe12142team06.domain.application.service;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.domain.payment.service.PaymentService;
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.service.EscortService;
import com.back.nbe12142team06.global.exception.InvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationCancelServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EscortProfileRepository escortProfileRepository;

    @Mock
    private NoShowPenaltyService noShowPenaltyService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NoticeService noticeService;

    @InjectMocks
    private ApplicationService applicationService;

    @Mock
    private EscortService escortService;

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    @DisplayName("동행 진행 중이거나 완료된 공고는 지원을 취소할 수 없다")
    void cancel_whenPostIsInProgressOrCompleted_throwsException(PostStatus status) {

        // given
        Long applicationId = 1L;
        Long escortId = 2L;
        Long postId = 3L;

        User escort = mock(User.class);
        Application application = mock(Application.class);
        Post post = mock(Post.class);

        when(applicationRepository.findEscortIdById(applicationId))
                .thenReturn(Optional.of(escortId));
        when(userRepository.findByIdWithLock(escortId))
                .thenReturn(Optional.of(escort));

        when(applicationRepository.findPostIdById(applicationId))
                .thenReturn(Optional.of(postId));

        when(applicationRepository.findByIdWithLock(applicationId))
                .thenReturn(Optional.of(application));

        when(escort.getId()).thenReturn(escortId);
        when(application.getStatus())
                .thenReturn(ApplicationStatus.ACCEPTED);
        when(post.getPostStatus()).thenReturn(status);

        when(postRepository.findByIdWithLock(postId))
                .thenReturn(Optional.of(post));

        // when & then
        InvalidException exception = assertThrows(
                InvalidException.class,
                () -> applicationService.cancel(applicationId, escortId)
        );

        assertEquals(
                "동행 진행 중이거나 완료된 공고는 지원을 취소할 수 없습니다.",
                exception.getMessage()
        );

        // 노쇼·환불·알림 등 후속 처리가 실행되지 않아야 한다
        verify(application, never()).noShow();
        verify(post, never()).reopen();
        verify(post, never()).matchedCancel();
        verifyNoInteractions(
                escortProfileRepository,
                noShowPenaltyService,
                paymentService,
                noticeService
        );
    }

    @Test
    @DisplayName("대기 중인 지원은 정상적으로 취소할 수 있다")
    void cancel_whenPending_success() {
        Long applicationId = 1L;
        Long escortId = 2L;
        Long postId = 3L;

        User escort = mock(User.class);
        Application application = mock(Application.class);
        Post post = mock(Post.class);

        when(applicationRepository.findEscortIdById(applicationId))
                .thenReturn(Optional.of(escortId));
        when(userRepository.findByIdWithLock(escortId))
                .thenReturn(Optional.of(escort));

        when(applicationRepository.findPostIdById(applicationId))
                .thenReturn(Optional.of(postId));

        when(postRepository.findByIdWithLock(postId))
                .thenReturn(Optional.of(post));

        when(applicationRepository.findByIdWithLock(applicationId))
                .thenReturn(Optional.of(application));

        when(escort.getId()).thenReturn(escortId);
        when(application.getStatus())
                .thenReturn(ApplicationStatus.PENDING);

        // when
        applicationService.cancel(applicationId, escortId);

        // then
        verify(application).cancel();
        verifyNoInteractions(
                noShowPenaltyService,
                paymentService,
                noticeService
        );
    }

    @Test
    @DisplayName("매칭된 공고는 지원 취소 시 노쇼 처리 후 재모집된다")
    void cancel_whenMatched_success() {
        // given
        Long applicationId = 1L;
        Long escortId = 2L;
        Long postId = 3L;

        User escort = mock(User.class);
        Application application = mock(Application.class);
        Post post = mock(Post.class);
        EscortProfile escortProfile = mock(EscortProfile.class);

        when(applicationRepository.findEscortIdById(applicationId))
                .thenReturn(Optional.of(escortId));
        when(userRepository.findByIdWithLock(escortId))
                .thenReturn(Optional.of(escort));

        when(applicationRepository.findPostIdById(applicationId))
                .thenReturn(Optional.of(postId));

        when(applicationRepository.findByIdWithLock(applicationId))
                .thenReturn(Optional.of(application));
        when(application.getEscort()).thenReturn(escort);
        when(escort.getId()).thenReturn(escortId);
        when(application.getStatus()).thenReturn(ApplicationStatus.ACCEPTED);

        when(post.getId()).thenReturn(postId);
        when(post.getPostStatus()).thenReturn(PostStatus.MATCHED);
        when(post.getRecruitEndAt())
                .thenReturn(LocalDateTime.now().plusDays(1));

        when(postRepository.findByIdWithLock(postId))
                .thenReturn(Optional.of(post));
        when(escortProfileRepository.findById(escortId))
                .thenReturn(Optional.of(escortProfile));

        // when
        applicationService.cancel(applicationId, escortId);

        // then
        verify(application).noShow();
        verify(escortProfile).increaseNoShowCount();
        verify(noShowPenaltyService).noShow(application);
        verify(post).reopen();
        verify(escortService).refreshGrade(escortProfile);

        // 모집 기간이 남아 있으므로 환불하지 않음
        verifyNoInteractions(paymentService);

        // 의뢰인에게 알림 전송
        verify(noticeService).send(
                any(), any(), any()
        );
    }
}
