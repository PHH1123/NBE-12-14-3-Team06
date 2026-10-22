package com.back.nbe12142team06.domain.notification.service;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.event.NotificationEvent;
import com.back.nbe12142team06.domain.notification.repository.EmitterRepository;
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * NoticeService.send() 단위 테스트.
 *
 * 스프링 컨텍스트를 띄우지 않고 NoticeService 인스턴스를 직접 만들어 호출합니다.
 * NoticeServiceTest(@SpringBootTest) 와 달리 프록시를 거치지 않는 것이 핵심입니다 —
 * send() 는 @Async 라서 컨텍스트 안에서 부르면 다른 스레드로 넘어가 버려
 * 테스트가 저장·발행을 보기도 전에 끝납니다. 여기서는 그냥 평범한 메서드 호출입니다.
 *
 * send() 의 책임은 "알림을 저장하고 이벤트를 발행하는 것"까지입니다.
 * 실제 SSE 전송은 NotificationEventListener 가 커밋 후에 하므로 여기서는 다루지 않습니다.
 */
@ExtendWith(MockitoExtension.class)
@Transactional
class NoticeServiceSendUnitTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmitterRepository emitterRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NoticeService noticeService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;
    @Captor
    private ArgumentCaptor<NotificationEvent> eventCaptor;

    private User createClient() {
        return User.builder()
                .username("client01")
                .password("password1!")
                .email("client01@email.com")
                .name("의뢰인")
                .role(Role.CLIENT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(1960, 11, 11))
                .phoneNum("010-1234-1234")
                .region("서울")
                .build();
    }

    /** 저장된 뒤의 알림 — 실제 save() 가 돌려주는, 식별자가 붙은 엔티티를 흉내 냅니다. */
    private Notification savedNotification(User receiver, String content, String url) {
        return Notification.builder()
                .id(1L)
                .content(content)
                .url(url)
                .receiver(receiver)
                .build();
    }

    @Test
    @DisplayName("[NoticeService.send] 받는 사람·내용·링크를 그대로 담아 알림을 저장한다")
    void savesNotificationWithGivenArguments() {
        User client = createClient();
        String content = "동행매니저님이 공고에 지원하셨습니다.";
        String url = "/client/posts/1";
        doReturn(savedNotification(client, content, url)).when(notificationRepository).save(any());
        doReturn(client).when(userRepository).getReferenceById(any());

        noticeService.send(client.getId(), content, url);

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getReceiver()).isSameAs(client);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("[NoticeService.send] 새로 만든 알림은 읽지 않은 상태다")
    void savesNotificationAsUnread() {
        User client = createClient();
        doReturn(savedNotification(client, "알림", null)).when(notificationRepository).save(any());

        noticeService.send(client.getId(), "알림", null);

        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().isRead()).isFalse();
    }

    @Test
    @DisplayName("[NoticeService.send] 저장된 알림을 그대로 이벤트에 실어 발행한다")
    void publishesEventCarryingSavedNotification() {
        User client = createClient();
        String content = "동행매니저님이 공고에 지원하셨습니다.";
        String url = "/client/posts/1";
        // 저장 전 알림에는 id 가 없습니다. 이벤트에는 id 가 붙은 "저장된" 쪽이 실려야
        // 리스너가 만드는 NotificationResponse 에 id 가 들어갑니다.
        Notification saved = savedNotification(client, content, url);
        doReturn(saved).when(notificationRepository).save(any());

        noticeService.send(client.getId(), content, url);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.notification()).isSameAs(saved);
        assertThat(event.notification().getId()).isEqualTo(1L);
        assertThat(event.receiverId()).isEqualTo(client.getId());
        assertThat(event.content()).isEqualTo(content);
        assertThat(event.url()).isEqualTo(url);
    }

    @Test
    @DisplayName("[NoticeService.send] 링크가 없는 알림도 전송한다")
    void sendsNotificationWithoutUrl() {
        User client = createClient();
        String content = "동행매니저님이 공고에 지원하셨습니다.";
        // 지금 ApplicationService.apply() 가 url 로 null 을 넘깁니다.
        doReturn(savedNotification(client, content, null)).when(notificationRepository).save(any());

        noticeService.send(client.getId(), content, null);

        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUrl()).isNull();

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().url()).isNull();
    }

    @Test
    @DisplayName("[NoticeService.send] 알림을 저장한 뒤에 이벤트를 발행한다")
    void savesBeforePublishing() {
        User client = createClient();
        doReturn(savedNotification(client, "알림", null)).when(notificationRepository).save(any());

        noticeService.send(client.getId(), "알림", null);

        // 순서가 뒤집히면 리스너가 id 없는 알림을 받게 됩니다.
        InOrder order = inOrder(notificationRepository, eventPublisher);
        order.verify(notificationRepository).save(any());
        order.verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("[NoticeService.send] SSE 전송은 리스너에 맡기고 직접 하지 않는다")
    void doesNotPushToEmittersItself() {
        User client = createClient();
        doReturn(savedNotification(client, "알림", null)).when(notificationRepository).save(any());

        noticeService.send(client.getId(), "알림", null);

        // send() 가 직접 emitter 를 건드리면 커밋 전에 알림이 나가게 됩니다.
        verifyNoInteractions(emitterRepository);
    }
}
