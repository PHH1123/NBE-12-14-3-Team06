package com.back.nbe12142team06.domain.notification.repository

import org.springframework.stereotype.Repository
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Repository
class EmitterRepositoryImpl : EmitterRepository {
    private val emitterMap: ConcurrentMap<Long, ConcurrentMap<String, SseEmitter>> =
        ConcurrentHashMap()

    override fun save(userId: Long, emitterId: String, sseEmitter: SseEmitter): SseEmitter {
        emitterMap.computeIfAbsent(userId) { ConcurrentHashMap() }[emitterId] = sseEmitter

        return sseEmitter
    }

    override fun findAllByUserId(userId: Long): Map<String, SseEmitter> {
        return emitterMap.getOrDefault(userId, mapOf())
    }

    override fun delete(userId: Long, emitterId: String) {
        emitterMap.computeIfPresent(userId) { key: Long, userEmitters: ConcurrentMap<String, SseEmitter> ->
            userEmitters.remove(emitterId)
            userEmitters.ifEmpty { null }
        }
    }
}
