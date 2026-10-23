package com.mapoker.interfaces.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapoker.application.auth.GoogleAuthService;
import com.mapoker.application.auth.User;
import com.mapoker.application.auth.UserService;
import com.mapoker.application.history.HandHistoryService;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.infrastructure.config.CorsProperties;
import com.mapoker.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.messaging.web.csrf.XorCsrfChannelInterceptor;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.web.socket.WebSocketHandler;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(CorsProperties.class)
class AuthSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserService users;
    @MockitoBean GoogleAuthService google;
    @MockitoBean UserTableHistoryService history;
    @MockitoBean HandHistoryService hands;

    @Test
    void rejectsLoginWithoutCsrfToken() throws Exception {
        mvc.perform(post("/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"token\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(google);
    }

    @Test
    void tokenRoundTripRotatesSessionAndInvalidatesPreLoginToken() throws Exception {
        var result = mvc.perform(get("/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        var token = mapper.readTree(result.getResponse().getContentAsString());
        var session = (MockHttpSession) result.getRequest().getSession();
        String originalId = session.getId();
        when(google.loginWithGoogle("token")).thenReturn(new GoogleAuthService.LoginResult(
                new User(1L, "public-alice", "alice", "0000", null, LocalDateTime.now()), false));
        mvc.perform(post("/v1/auth/google").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"token\"}"))
                .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(originalId);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        mvc.perform(post("/v1/auth/logout").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isForbidden());
        var next = mvc.perform(get("/v1/auth/csrf").session(session)).andReturn();
        var nextToken = mapper.readTree(next.getResponse().getContentAsString());
        mvc.perform(post("/v1/auth/logout").session(session)
                .header(nextToken.get("headerName").asText(), nextToken.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void csrfTokenIsBoundToItsSession() throws Exception {
        var result = mvc.perform(get("/v1/auth/csrf")).andReturn();
        var token = mapper.readTree(result.getResponse().getContentAsString());
        mvc.perform(post("/v1/auth/logout").with(user("alice"))
                .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void httpTokenWorksForWebSocketConnectAndMissingTokenIsRejected() throws Exception {
        var result = mvc.perform(get("/v1/auth/csrf")).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        var session = (MockHttpSession) result.getRequest().getSession();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var deferred = new HttpSessionCsrfTokenRepository().loadDeferredToken(request, response);
        request.setAttribute(DeferredCsrfToken.class.getName(), deferred);
        new XorCsrfTokenRequestAttributeHandler().handle(request, response, deferred::get);
        var attributes = new HashMap<String, Object>();
        new CsrfTokenHandshakeInterceptor().beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionAttributes(attributes);
        headers.setNativeHeader(json.get("headerName").asText(), json.get("token").asText());
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var interceptor = new XorCsrfChannelInterceptor();
        var channel = new ExecutorSubscribableChannel();
        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        var missing = StompHeaderAccessor.create(StompCommand.CONNECT);
        missing.setSessionAttributes(attributes);
        assertThatThrownBy(() -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], missing.getMessageHeaders()), channel))
                .isInstanceOf(InvalidCsrfTokenException.class);
    }

    @Test
    void rejectsUntrustedCredentialedOrigin() throws Exception {
        mvc.perform(options("/v1/auth/google").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/v1/auth/google").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
