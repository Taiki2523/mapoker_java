package com.mapoker.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketSecurityConfig {

    @Bean
    @Profile("local")
    public AbstractSecurityWebSocketMessageBrokerConfigurer localWebSocketSecurityConfigurer() {
        return new AbstractSecurityWebSocketMessageBrokerConfigurer() {
            @Override
            protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
                messages.anyMessage().permitAll();
            }

            @Override
            protected boolean sameOriginDisabled() {
                return true;
            }
        };
    }

    @Bean
    @Profile("!local")
    public AbstractSecurityWebSocketMessageBrokerConfigurer webSocketSecurityConfigurer() {
        return new AbstractSecurityWebSocketMessageBrokerConfigurer() {
            @Override
            protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
                messages
                        .nullDestMatcher().authenticated()
                        .simpSubscribeDestMatchers("/topic/**", "/user/queue/**").authenticated()
                        .anyMessage().authenticated();
            }

            @Override
            protected boolean sameOriginDisabled() {
                return true;
            }
        };
    }
}
