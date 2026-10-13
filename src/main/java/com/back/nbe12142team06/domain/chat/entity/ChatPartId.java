package com.back.nbe12142team06.domain.chat.entity;

import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Embeddable
@Data
public class ChatPartId implements Serializable {
    private Long userId;
    private Long chatRoomId;
}
