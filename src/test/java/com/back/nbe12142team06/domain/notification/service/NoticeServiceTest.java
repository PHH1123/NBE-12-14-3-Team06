package com.back.nbe12142team06.domain.notification.service;

import com.back.nbe12142team06.domain.notification.entity.Notification;
import com.back.nbe12142team06.domain.notification.event.NotificationEvent;
import com.back.nbe12142team06.domain.notification.repository.NotificationRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoticeServiceTest {

    @Autowired
    private NoticeService noticeService;
    @MockitoSpyBean
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;

    private User createClient(boolean addId) {
        User client = User.builder()
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
        if (addId) {
            try {
                Field clientIdField = client.getClass().getDeclaredField("id");
                clientIdField.setAccessible(true);
                clientIdField.set(client, 1L);
            } catch (Exception e) {
            }
        }
        return client;
    }

    private User createClient() {
        return createClient(true);
    }

    private User createEscort(boolean addId) {
        User escort = User.builder()
                .username("escort01")
                .password("password1!")
                .email("escort01@email.com")
                .name("동행매니저")
                .role(Role.ESCORT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(2000, 1, 1))
                .phoneNum("010-5678-5678")
                .region("서울")
                .build();
        if (addId) {
            try {
                Field escortIdField = escort.getClass().getDeclaredField("id");
                escortIdField.setAccessible(true);
                escortIdField.set(escort, 2L);
            } catch (Exception e) {
            }
        }
        return escort;
    }

    private User createEscort() {
        return createEscort(true);
    }

    private Notification createNotification(User receiver, String content, String url, Long id) {
        return Notification.builder()
                .id(id)
                .content(content)
                .url(url)
                .receiver(receiver)
                .build();
    }

    @Test
    @DisplayName("[NoticeService] SSE 연결 성공")
    void connect() {
        User client = createClient();

        SseEmitter emitter = noticeService.connect(client.getId());

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("[NoticeService] SSE ")
    void connectFailed() {
        User client = createClient();

        SseEmitter emitter = noticeService.connect(client.getId());

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("[NoticeService] 알림 전송")
    void send() {
        User client = createClient();
        User escort = createEscort();
        String content = escort.getName() + "님이 알림을 전송했습니다.";

        noticeService.connect(client.getId());
        noticeService.send(client.getId(), content, null);
    }

    @Test
    @DisplayName("[NoticeService] 알림 목록 가져오기")
    void getPage() {
        User client = createClient(false);
        userRepository.save(client);
        notificationRepository.save(createNotification(client, "알림1", null, null));
        notificationRepository.save(createNotification(client, "알림2", null, null));

        Page<Notification> notifications = noticeService.getPage(client.getId(), PageRequest.of(0, 10, Sort.Direction.DESC, "id"));
        List<Notification> notificationList = notifications.getContent();

        assertThat(notificationList.size()).isEqualTo(2);
        assertThat(notificationList.get(0).getContent()).isEqualTo("알림2");
        assertThat(notificationList.get(1).getContent()).isEqualTo("알림1");
        assertThat(notificationList.get(0).isRead()).isFalse();
        assertThat(notificationList.get(1).isRead()).isFalse();
    }

    @Test
    @DisplayName("[NoticeService] 알림 읽기 처리")
    void readList() {
        User client = createClient();
        Notification notification1 = createNotification(client, "알림1", null, null);
        Notification notification2 = createNotification(client, "알림2", null, null);

        doReturn(new PageImpl<>(new ArrayList<>(List.of(notification2, notification1))))
                .when(notificationRepository).findByReceiverId(any(), any());

        Page<Notification> notifications = noticeService.readList(null, null);
        List<Notification> notificationList = notifications.getContent();

        assertThat(notificationList.size()).isEqualTo(2);
        assertThat(notificationList.get(0).getContent()).isEqualTo("알림2");
        assertThat(notificationList.get(1).getContent()).isEqualTo("알림1");
        assertThat(notificationList.get(0).isRead()).isTrue();
        assertThat(notificationList.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("[NoticeService] 단건 읽기")
    void read() {
        User client = createClient();
        Notification notification = createNotification(client, "알림1", null, null);

        doReturn(Optional.of(notification))
                .when(notificationRepository).findByIdAndDeletedAtIsNull(any());

        Notification readNotification = noticeService.read(client.getId(), null);

        assertThat(readNotification.getContent()).isEqualTo("알림1");
        assertThat(readNotification.isRead()).isTrue();
    }

    @Test
    @DisplayName("[NoticeService] 알림 제거")
    void removeNotification() {
        User client = createClient();
        Notification notification = createNotification(client, "알림1", null, null);

        doReturn(Optional.of(notification))
                .when(notificationRepository).findByIdAndDeletedAtIsNull(any());

        assertThat(notification.getDeletedAt()).isNull();

        notification = noticeService.removeNotification(client.getId(), null);

        assertThat(notification.getDeletedAt()).isNotNull();
    }
}