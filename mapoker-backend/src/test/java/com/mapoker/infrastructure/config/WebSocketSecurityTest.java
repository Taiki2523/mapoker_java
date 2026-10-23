package com.mapoker.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketSecurityTest {
    private final WebSocketSecurityConfigProd config = new WebSocketSecurityConfigProd();

    @Test
    void authenticatedUsersCanSubscribeButCannotPublishOrSubscribeToRawUserQueues() {
        var manager = config.messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var auth = UsernamePasswordAuthenticationToken.authenticated("public-alice", null, List.of());
        for (String destination : List.of("/topic/tables/table-1/game", "/topic/tables/table-1/members", "/user/queue/hole-cards")) {
            assertThat(manager.check(() -> auth, message(SimpMessageType.SUBSCRIBE, destination)).isGranted()).isTrue();
            assertThat(manager.check(() -> auth, message(SimpMessageType.MESSAGE, destination)).isGranted()).isFalse();
        }
        assertThat(manager.check(() -> auth, message(SimpMessageType.SUBSCRIBE, "/queue/hole-cards-other-session")).isGranted()).isFalse();
        assertThat(manager.check(() -> auth, message(SimpMessageType.SUBSCRIBE, "/topic/**")).isGranted()).isFalse();
    }

    @Test
    void anonymousUsersCannotConnectOrSubscribe() {
        var manager = config.messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(manager.check(() -> anonymous, message(SimpMessageType.CONNECT, null)).isGranted()).isFalse();
        assertThat(manager.check(() -> anonymous, message(SimpMessageType.SUBSCRIBE, "/topic/tables/table-1/game")).isGranted()).isFalse();
    }

    private Message<?> message(SimpMessageType type, String destination) {
        var headers = SimpMessageHeaderAccessor.create(type);
        if (destination != null) headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
