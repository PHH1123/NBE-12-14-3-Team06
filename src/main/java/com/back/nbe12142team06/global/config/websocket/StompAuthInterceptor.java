package com.back.nbe12142team06.global.config.websocket;

import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.service.AuthTokenService;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final AuthTokenService authTokenService;
    private final ChatPartRepository chatPartRepository;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor.getUser() == null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new MessagingException("인증 토큰이 없습니다.");
            }
            Map<String, Object> payload = authTokenService.payload(authorization.split(" ")[1]);

            Long id = ((Number)payload.get("id")).longValue();
            String username = (String) payload.get("username");
            Role role = Role.valueOf((String) payload.get("role"));

            List<GrantedAuthority> authorities = role.toAuthorities();
            SecurityUser securityUser = new SecurityUser(id, username, authorities);
            Principal principal = new UsernamePasswordAuthenticationToken(securityUser, null, null);

            // 웹소켓 세션에 JWT 토큰 정보 등록
            accessor.setUser(principal);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && accessor.getDestination().startsWith("/topic/chat/")) {
            Long roomId = Long.valueOf(accessor.getDestination().split("/topic/chat/")[1]);
            UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) accessor.getUser();
            SecurityUser user = (SecurityUser) authToken.getPrincipal();
            chatPartRepository.findByUserIdAndChatRoomId(user.getId(), roomId)
                    .orElseThrow(() -> new InvalidException(121, "잘못된 채팅방에 접속하셨습니다."));
        }

        return message;
    }
}
