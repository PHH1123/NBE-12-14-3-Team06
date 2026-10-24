package com.back.nbe12142team06.domain.notification.repository

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface EmitterRepository {
    fun save(userId: Long, emitterId: String, sseEmitter: SseEmitter): SseEmitter

    fun findAllByUserId(userId: Long): Map<String, SseEmitter>

    fun delete(userId: Long, emitterId: String)
}
