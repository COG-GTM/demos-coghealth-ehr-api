package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private User bob;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 3_600_000);

        bob = JwtTokenProviderTest.activeUser("bob");
        UserDetailsService userDetailsService = username -> bob;

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesActiveUser() throws Exception {
        Authentication auth = runFilter(tokenProvider.generateTokenFromUsername("bob"));

        assertNotNull(auth);
        assertEquals("bob", auth.getName());
    }

    @Test
    void doesNotAuthenticateDisabledUserWithUnexpiredToken() throws Exception {
        String token = tokenProvider.generateTokenFromUsername("bob");
        bob.setEnabled(false);

        assertNull(runFilter(token));
    }

    @Test
    void doesNotAuthenticateLockedUserWithUnexpiredToken() throws Exception {
        String token = tokenProvider.generateTokenFromUsername("bob");
        bob.setAccountNonLocked(false);

        assertNull(runFilter(token));
    }

    private Authentication runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/patients");
        request.addHeader("Authorization", "Bearer " + token);
        Authentication[] seen = new Authentication[1];
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req,
                                   HttpServletResponse res) {
                seen[0] = SecurityContextHolder.getContext().getAuthentication();
            }
        });
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return seen[0];
    }
}
