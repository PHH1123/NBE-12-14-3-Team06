package com.back.nbe12142team06.global.config.websocket

import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.service.AuthTokenService
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.security.SecurityUser
import lombok.RequiredArgsConstructor
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import java.security.Principal
import java.util.function.Supplier

@Component
class StompAuthInterceptor(
    private val authTokenService: AuthTokenService,
    private val chatPartRepository: ChatPartRepository
) : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor: StompHeaderAccessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                ?: return message

        // 앱에서 사용할 경우를 대비해 아래를 남겨둡니다.
        // 앱에서는 cookie가 아니라 header 기반으로 해야 합니다.
        // 아니면 Spring Security에서 Header 기반으로 검증하면 아래 없어도 됩니다.
        if (accessor.user == null && StompCommand.CONNECT == accessor.command) {
            val authorization = accessor.getFirstNativeHeader("Authorization")
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw MessagingException("인증 토큰이 없습니다.")
            }
            val payload = authTokenService.payload(authorization.removePrefix("Bearer ").trim())

            val id = (payload["id"] as Number).toLong()
            val username = payload["username"] as String
            val role = Role.valueOf((payload["role"] as String))

            val authorities = role.toAuthorities()
            val securityUser = SecurityUser(id, username, authorities)
            val principal: Principal = UsernamePasswordAuthenticationToken(securityUser, null, mutableListOf())

            // 웹소켓 세션에 JWT 토큰 정보 등록
            accessor.setUser(principal)
        }

        if (StompCommand.SUBSCRIBE == accessor.command && accessor.destination?.startsWith("/topic/chat/") == true
        ) {
            val destination = accessor.destination ?: return message
            val roomId = destination.removePrefix("/topic/chat/").toLongOrNull()
                ?: throw InvalidException(121, "잘못된 채팅방에 접속하셨습니다.")
            val authToken = accessor.user as UsernamePasswordAuthenticationToken
            val user = authToken.principal as SecurityUser
            chatPartRepository.findByUserIdAndChatRoomId(user.id, roomId)
                ?: throw InvalidException(121, "잘못된 채팅방에 접속하셨습니다.")
        }

        return message
    }
}
