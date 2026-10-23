package com.back.nbe12142team06.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class EmitterRepositoryImpl implements EmitterRepository {

    private final Map<Long, Map<String, SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    @Override
    public SseEmitter save(Long userId, String emitterId, SseEmitter sseEmitter) {
        emitterMap.computeIfAbsent(userId, key -> new ConcurrentHashMap<>()).put(emitterId, sseEmitter);
        return sseEmitter;
    }

    @Override
    public Map<String, SseEmitter> findAllByUserId(Long userId) {
        return emitterMap.getOrDefault(userId, Map.of());
    }

    @Override
    public void delete(Long userId, String emitterId) {
        emitterMap.computeIfPresent(userId, (key, userEmitters) -> {
            userEmitters.remove(emitterId);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }
}
