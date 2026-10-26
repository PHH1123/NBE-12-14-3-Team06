package com.back.nbe12142team06.domain.post.service

import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository
import com.back.nbe12142team06.domain.application.repository.EscortProgressLogRepository
import com.back.nbe12142team06.domain.payment.service.PaymentService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.post.repository.PostRepository
import com.back.nbe12142team06.domain.ride.service.RideService
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import com.back.nbe12142team06.domain.user.service.EscortService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PostLocationCleanupServiceTest {

    @Mock
    private lateinit var postRepository: PostRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var applicationRepository: ApplicationRepository

    // PostService 는 코틀린이라 생성자에 null 을 넣을 수 없다. 이 테스트에서 안 쓰더라도 의존성을 모두 mock 으로 채운다.
    @Mock
    private lateinit var escortProgressLogRepository: EscortProgressLogRepository

    @Mock
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var rideService: RideService

    @Mock
    private lateinit var escortService: EscortService

    @InjectMocks
    private lateinit var postService: PostService

    @Test
    fun matchedCancelAlsoClearsAnyStoredLocation() {
        val postId = 1L
        val clientId = 2L
        val post = mock(Post::class.java)
        val client = mock(User::class.java)
        // Kotlin 에서 when 은 예약어라 Mockito.when 대신 같은 동작을 하는 BDDMockito.given 을 쓴다
        given(userRepository.findById(clientId)).willReturn(Optional.of(client))
        given(postRepository.findByIdWithLock(postId)).willReturn(post)
        given(client.role).willReturn(Role.CLIENT)
        given(client.id).willReturn(clientId)
        given(post.client).willReturn(client)
        given(post.postStatus).willReturn(PostStatus.MATCHED)
        given(post.id).willReturn(postId)
        given(applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.ACCEPTED))
            .willReturn(emptyList())
        given(applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.PENDING))
            .willReturn(emptyList())

        postService.matchedCancel(postId, clientId)

        verify(post).matchedCancel()
        verify(applicationRepository).clearLocationsByPostId(postId)
    }
}
