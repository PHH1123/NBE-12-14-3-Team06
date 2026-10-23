package com.back.nbe12142team06.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface EmitterRepository {

    SseEmitter save(Long userId, String emitterId, SseEmitter sseEmitter);

    Map<String, SseEmitter> findAllByUserId(Long userId);

    void delete(Long userId, String emitterId);
}
