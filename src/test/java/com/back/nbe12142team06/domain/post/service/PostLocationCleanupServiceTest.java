package com.back.nbe12142team06.domain.post.service;

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository;
import com.back.nbe12142team06.domain.payment.service.PaymentService;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.ride.service.RideService;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.service.EscortService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostLocationCleanupServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationRepository applicationRepository;
    // PostService 는 코틀린이라 생성자에 null 을 넣을 수 없다. 이 테스트에서 안 쓰더라도 의존성을 모두 mock 으로 채운다.
    @Mock private EscortProgressLogRepository escortProgressLogRepository;
    @Mock private EscortProfileRepository escortProfileRepository;
    @Mock private PaymentService paymentService;
    @Mock private RideService rideService;
    @Mock private EscortService escortService;

    @InjectMocks private PostService postService;

    @Test
    void matchedCancelAlsoClearsAnyStoredLocation() {
        Long postId = 1L;
        Long clientId = 2L;
        Post post = mock(Post.class);
        User client = mock(User.class);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(client.getRole()).thenReturn(Role.CLIENT);
        when(client.getId()).thenReturn(clientId);
        when(post.getClient()).thenReturn(client);
        when(post.getPostStatus()).thenReturn(PostStatus.MATCHED);
        when(post.getId()).thenReturn(postId);
        when(applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of());
        when(applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.PENDING))
                .thenReturn(List.of());

        postService.matchedCancel(postId, clientId);

        verify(post).matchedCancel();
        verify(applicationRepository).clearLocationsByPostId(postId);
    }
}
