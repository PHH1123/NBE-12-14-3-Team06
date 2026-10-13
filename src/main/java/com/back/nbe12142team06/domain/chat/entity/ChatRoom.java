package com.back.nbe12142team06.domain.chat.entity;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String lastChatContent;

    private LocalDateTime lastChatSentAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatPart> chatParts = new ArrayList<>();

    // 채팅방 입장 내역 추가
    public ChatPart addChatPart(User sender, User receiver) {
        ChatPart chatPart = ChatPart.builder()
                .user(sender)
                .chatRoom(this)
                .role(sender.getRole())
                .build();
        chatParts.add(chatPart);
        chatParts.add(ChatPart.builder()
                .user(receiver)
                .chatRoom(this)
                .role(receiver.getRole())
                .build());
        return chatPart;
    }

    // 마지막 채팅 업데이트
    public void updateLastChat(String content, LocalDateTime sentAt) {
        this.lastChatContent = content;
        this.lastChatSentAt = sentAt;
    }
}
