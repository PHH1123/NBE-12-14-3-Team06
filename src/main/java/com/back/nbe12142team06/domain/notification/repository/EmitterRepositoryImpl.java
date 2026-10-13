package com.back.nbe12142team06.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class EmitterRepositoryImpl implements EmitterRepository {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final Map<String, Object> eventCache = new ConcurrentHashMap<>();

    @Override
    public SseEmitter save(String emitterId, SseEmitter sseEmitter) {
        emitterMap.put(emitterId, sseEmitter);
        return sseEmitter;
    }

    @Override
    public void saveEventCache(String emitterId, Object event) {
        eventCache.put(emitterId, event);
    }

    @Override
    public Map<String, SseEmitter> findAllEmitterStartWithByUserId(Long userId) {
        return emitterMap.entrySet().stream()
                .filter(emitterMap -> emitterMap.getKey().startsWith(userId + "_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<String, Object> findAllEventCacheStartWithByUserId(Long userId) {
        return emitterMap.entrySet().stream()
                .filter(emitterMap -> emitterMap.getKey().startsWith(userId + "_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void deleteById(String emitterId) {
        emitterMap.remove(emitterId);
    }

    @Override
    public void deleteAllEmitterStartWithUserId(Long userId) {
        emitterMap.forEach((key, emitter) -> {
            if (key.startsWith(String.valueOf(userId))) emitterMap.remove(key);
        });
    }

    @Override
    public void deleteAllEventCacheStartWithUserId(Long userId) {
        eventCache.forEach((key, event) -> {
            if (key.startsWith(String.valueOf(userId))) eventCache.remove(key);
        });
    }
}
