package com.mapoker.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@Profile("!local")
@EnableWebSocketSecurity
public class WebSocketSecurityConfigProd {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
                .simpTypeMatchers(SimpMessageType.CONNECT).authenticated()
                .simpTypeMatchers(SimpMessageType.DISCONNECT, SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE).permitAll()
                .simpSubscribeDestMatchers("/topic/tables/*/game", "/topic/tables/*/members", "/user/queue/hole-cards").authenticated()
                .anyMessage().denyAll()
                .build();
    }
}
