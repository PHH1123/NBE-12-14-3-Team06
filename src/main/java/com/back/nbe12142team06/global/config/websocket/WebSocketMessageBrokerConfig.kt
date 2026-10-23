package com.back.nbe12142team06.global.config.websocket

import lombok.RequiredArgsConstructor
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration

@Configuration
@EnableWebSocketMessageBroker
class WebSocketMessageBrokerConfig(
    private val stompAuthInterceptor: StompAuthInterceptor
) : WebSocketMessageBrokerConfigurer
{
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/chat")
            .setAllowedOriginPatterns("*", "http://localhost:3000", "https://*.devcproject.cloud")
        // .withSockJS();
        // 같은 세션에 들어온 메시지 순서 보장
        registry.setPreserveReceiveOrder(true)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app")
        registry.enableSimpleBroker("/topic")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthInterceptor)
    }

    override fun configureWebSocketTransport(registry: WebSocketTransportRegistration) {
        registry.setSendBufferSizeLimit(8192)
    }
}
