package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        return request;
    }

    @Test
    void rejectedTokenRunsChainOnceAndDownstreamFailurePropagates() throws Exception {
        UserDetails user = new User("dr.smith", "x", Collections.emptyList());
        when(tokenProvider.getUsernameFromToken("some.jwt.token")).thenReturn("dr.smith");
        when(userDetailsService.loadUserByUsername("dr.smith")).thenReturn(user);
        when(tokenProvider.validateToken("some.jwt.token", user)).thenReturn(false);
        doThrow(new ServletException("downstream")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(requestWithToken(), new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream");

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void disabledUserWithValidTokenIsNotAuthenticated() throws Exception {
        UserDetails user = User.withUsername("dr.smith").password("x")
                .authorities(Collections.emptyList()).disabled(true).build();
        when(tokenProvider.getUsernameFromToken("some.jwt.token")).thenReturn("dr.smith");
        when(userDetailsService.loadUserByUsername("dr.smith")).thenReturn(user);
        when(tokenProvider.validateToken("some.jwt.token", user)).thenReturn(true);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
