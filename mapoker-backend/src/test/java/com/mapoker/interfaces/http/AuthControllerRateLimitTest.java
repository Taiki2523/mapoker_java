package com.mapoker.interfaces.http;

import com.mapoker.interfaces.http.filter.LoginRateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerRateLimitTest {

    private LoginRateLimitFilter filter;
    private final FilterChain passThroughChain = (req, res) -> {};

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
    }

    private MockHttpServletRequest requestFromIp(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void allows10RequestsPerMinute() throws ServletException, IOException {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestFromIp("192.168.1.1"), response, passThroughChain);
            assertThat(response.getStatus())
                    .as("request %d should pass", i + 1)
                    .isNotEqualTo(429);
        }
    }

    @Test
    void returns429OnEleventhRequest() throws ServletException, IOException {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(requestFromIp("10.0.0.1"), new MockHttpServletResponse(), passThroughChain);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("10.0.0.1"), response, passThroughChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws ServletException, IOException {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(requestFromIp("10.1.1.1"), new MockHttpServletResponse(), passThroughChain);
        }

        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilter(requestFromIp("10.1.1.2"), responseB, passThroughChain);
        assertThat(responseB.getStatus()).isNotEqualTo(429);
    }
}
