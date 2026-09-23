package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.Cookie;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);

        UserDetails userDetails = new User("schen", "", Collections.emptyList());
        when(tokenProvider.getUsernameFromToken("jwt-value")).thenReturn("schen");
        when(userDetailsService.loadUserByUsername("schen")).thenReturn(userDetails);
        when(tokenProvider.validateToken(eq("jwt-value"), any())).thenReturn(true);

        JwtCookieService cookieService = new JwtCookieService();
        ReflectionTestUtils.setField(cookieService, "cookieName", "auth_token");
        ReflectionTestUtils.setField(cookieService, "secure", true);
        ReflectionTestUtils.setField(cookieService, "sameSite", "Lax");
        ReflectionTestUtils.setField(cookieService, "jwtExpirationInMs", 86400000);

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        ReflectionTestUtils.setField(filter, "cookieService", cookieService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesFromHttpOnlyCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("auth_token", "jwt-value"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("schen");
    }

    @Test
    void stillAuthenticatesFromBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-value");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void leavesContextUnauthenticatedWithoutToken() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
