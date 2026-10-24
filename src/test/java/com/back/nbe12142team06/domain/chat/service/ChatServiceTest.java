package com.back.nbe12142team06.domain.chat.service;

import com.back.nbe12142team06.domain.chat.entity.ChatMessage;
import com.back.nbe12142team06.domain.chat.entity.ChatPart;
import com.back.nbe12142team06.domain.chat.entity.ChatRoom;
import com.back.nbe12142team06.domain.chat.entity.MessageType;
import com.back.nbe12142team06.domain.chat.event.ChatMessageEvent;
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository;
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository;
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ChatServiceTest {

    private final String CREATE_ROOM_MESSAGE = "님이 채팅방을 개설했습니다.";

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    ApplicationEvents events;
    @MockitoSpyBean
    private ChatRoomRepository chatRoomRepository;
    @MockitoSpyBean
    private ChatMessageRepository chatMessageRepository;
    @MockitoSpyBean
    private UserService userService;
    @MockitoSpyBean
    private ChatPartRepository chatPartRepository;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private User createClient(boolean addId, Long id, String name) {
        User client = User.builder()
                .username("client01")
                .password("password1!")
                .email("client01@email.com")
                .name(name)
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
                clientIdField.set(client, id);
            } catch (Exception e) {
            }
        }
        return client;
    }

    private User createClient(Long id, String name) {
        return createClient(true, id, name);
    }

    private User createClient() {
        return createClient(true, 1L, "의뢰인");
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

    private ChatRoom createChatRoom(User sender, Long id) {
        return new ChatRoom(
                id, sender.getName() + CREATE_ROOM_MESSAGE, LocalDateTime.now(), true, new ArrayList<>()
        );
    }

    private ChatPart createChatPart(User user, ChatRoom chatRoom) {
        return new ChatPart(
                null, user, chatRoom, user.getRole(), true, LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("[ChatService] 채팅방 만들기 - 동행 매니저 -> 의뢰인")
    void createChatRoomEscortToClient() {
        User client = createClient();
        User escort = createEscort();

        doReturn(client)
                .when(userService).myProfile(client.getId());
        doReturn(escort)
                .when(userService).myProfile(escort.getId());
        doReturn(null)
                .when(chatRoomRepository).findByUser(any(), any());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chatRoomRepository).save(any());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chatMessageRepository).save(any());

        ChatRoom chatRoom = chatService.createChatRoom(escort.getId(), client.getId());

        assertThat(chatRoom.getLastChatContent()).isEqualTo(escort.getName() + CREATE_ROOM_MESSAGE);
        assertThat(chatRoom.getLastChatSentAt()).isBeforeOrEqualTo(LocalDateTime.now());

        for (ChatPart chatPart : chatRoom.getChatParts()) {
            assertThat(chatPart.getChatRoom()).isEqualTo(chatRoom);
            assertThat(chatPart.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("[ChatService] 채팅방 만들기")
    void createChatRoomClientToClient() {
        User client1 = createClient(1L, "의뢰인1");
        User client2 = createClient(2L, "의뢰인2");

        doReturn(client1)
                .when(userService).myProfile(client1.getId());
        doReturn(client2)
                .when(userService).myProfile(client2.getId());
        doReturn(null)
                .when(chatRoomRepository).findByUser(any(), any());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chatRoomRepository).save(any());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chatMessageRepository).save(any());

        ChatRoom chatRoom = chatService.createChatRoom(client1.getId(), client2.getId());

        assertThat(chatRoom.getLastChatContent()).isEqualTo(client1.getName() + CREATE_ROOM_MESSAGE);
        assertThat(chatRoom.getLastChatSentAt()).isBeforeOrEqualTo(LocalDateTime.now());

        for (ChatPart chatPart : chatRoom.getChatParts()) {
            assertThat(chatPart.getChatRoom()).isEqualTo(chatRoom);
            assertThat(chatPart.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("[ChatService] 채팅방 목록 조회")
    void getChatList() {
        User escort = createEscort(false);
        User client = createClient(false, null, "의뢰인");

        escort = userRepository.save(escort);
        client = userRepository.save(client);

        chatService.createChatRoom(escort.getId(), client.getId());

        List<ChatRoom> escortChatRoomList = chatService.getChatRoomList(escort.getId());
        List<ChatRoom> clientChatRoomList = chatService.getChatRoomList(client.getId());

        assertThat(escortChatRoomList.getFirst().getId()).isEqualTo(clientChatRoomList.getFirst().getId());
    }

    @Test
    @DisplayName("[ChatService] 메시지 전송")
    void sendMessage() {
        final String ESCORT_CONTENT = "안녕하세요 저는 동행매니저 입니다.";
        final String CLIENT_CONTENT = "안녕하세요 저는 의뢰인 입니다.";
        User escort = createEscort(true);
        User client = createClient(1L, "의뢰인");
        ChatRoom chatRoom = createChatRoom(escort, 1L);

        ChatPart escortChatPart = createChatPart(escort, chatRoom);
        ChatPart clientChatPart = createChatPart(client, chatRoom);

        doReturn(escortChatPart)
                .when(chatPartRepository).fetchJoinByUserIdAndChatRoomId(eq(escort.getId()), anyLong());
        doReturn(clientChatPart)
                .when(chatPartRepository).fetchJoinByUserIdAndChatRoomId(eq(client.getId()), anyLong());
        doReturn(List.of(clientChatPart))
                .when(chatPartRepository).findByUserIdNotAndChatRoomId(eq(escort.getId()), anyLong());
        doReturn(List.of(escortChatPart))
                .when(chatPartRepository).findByUserIdNotAndChatRoomId(eq(client.getId()), anyLong());
        doReturn(Optional.of(chatRoom))
                .when(chatRoomRepository).findById(anyLong());
        doAnswer(invocation -> {
            ChatMessage arg = invocation.getArgument(0);
            return new ChatMessage(1L, arg.getChatPart(), arg.getContent(), arg.getSentAt(), arg.getType());
        })
                .when(chatMessageRepository).save(any());

        ChatMessage escortChatMessage = chatService.sendMessage(chatRoom.getId(), escort.getId(), ESCORT_CONTENT);
        ChatMessage clientChatMessage = chatService.sendMessage(chatRoom.getId(), client.getId(), CLIENT_CONTENT);

        assertThat(escortChatMessage.getType()).isEqualTo(MessageType.TALK);
        assertThat(escortChatMessage.getContent()).isEqualTo(ESCORT_CONTENT);
        assertThat(escortChatMessage.getSentAt()).isBeforeOrEqualTo(LocalDateTime.now());

        assertThat(clientChatMessage.getType()).isEqualTo(MessageType.TALK);
        assertThat(clientChatMessage.getContent()).isEqualTo(CLIENT_CONTENT);
        assertThat(clientChatMessage.getSentAt()).isBeforeOrEqualTo(LocalDateTime.now());

        assertThat(events.stream(ChatMessageEvent.class))
                .element(0)
                .satisfies(e -> assertThat(e.chatMessageResponse().content()).isEqualTo(ESCORT_CONTENT));
        assertThat(events.stream(ChatMessageEvent.class))
                .element(1)
                .satisfies(e -> assertThat(e.chatMessageResponse().content()).isEqualTo(CLIENT_CONTENT));
    }

    @Test
    @DisplayName("[ChatService] 메시지 조회")
    void getChatMessage() {
        final String ESCORT_CONTENT = "안녕하세요 저는 동행매니저 입니다.";
        final String CLIENT_CONTENT = "안녕하세요 저는 의뢰인 입니다.";
        User escort = createEscort(false);
        User client = createClient(false, null, "의뢰인");

        escort = userRepository.save(escort);
        client = userRepository.save(client);

        ChatRoom chatRoom = chatService.createChatRoom(escort.getId(), client.getId());
        chatService.sendMessage(chatRoom.getId(), escort.getId(), ESCORT_CONTENT);
        chatService.sendMessage(chatRoom.getId(), client.getId(), CLIENT_CONTENT);
        Pageable pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id");
        List<ChatMessage> escortMessages = chatService.getMessages(escort.getId(), chatRoom.getId(), pageable).getContent();
        List<ChatMessage> clientMessages = chatService.getMessages(client.getId(), chatRoom.getId(), pageable).getContent();

        assertThat(escortMessages.get(0).getContent()).isEqualTo(CLIENT_CONTENT);
        assertThat(escortMessages.get(1).getContent()).isEqualTo(ESCORT_CONTENT);
        assertThat(escortMessages.get(2).getContent()).isEqualTo(escort.getName() + CREATE_ROOM_MESSAGE);

        assertThat(clientMessages.get(0).getContent()).isEqualTo(CLIENT_CONTENT);
        assertThat(clientMessages.get(1).getContent()).isEqualTo(ESCORT_CONTENT);
        assertThat(clientMessages.get(2).getContent()).isEqualTo(escort.getName() + CREATE_ROOM_MESSAGE);
    }
}