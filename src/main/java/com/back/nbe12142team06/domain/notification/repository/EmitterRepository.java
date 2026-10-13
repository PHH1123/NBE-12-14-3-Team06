package com.back.nbe12142team06.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Repository
public interface EmitterRepository {

    SseEmitter save(String emitterId, SseEmitter sseEmitter);

    void saveEventCache(String emitterId, Object event);

    Map<String, SseEmitter> findAllEmitterStartWithByUserId(Long userId);

    Map<String, Object> findAllEventCacheStartWithByUserId(Long userId);

    void deleteById(String emitterId);

    void deleteAllEmitterStartWithUserId(Long userId);

    void deleteAllEventCacheStartWithUserId(Long userId);
}
