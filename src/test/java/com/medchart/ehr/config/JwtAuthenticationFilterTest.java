package com.medchart.ehr.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "Bearer token-value";

    private JwtTokenProvider tokenProvider;
    private UserDetailsService userDetailsService;
    private MeterRegistry meterRegistry;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(UserDetailsService.class);
        meterRegistry = new SimpleMeterRegistry();
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        ReflectionTestUtils.setField(filter, "meterRegistry", meterRegistry);
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsExpiredTokensByReason() throws Exception {
        when(tokenProvider.getUsernameFromToken(anyString()))
            .thenThrow(new ExpiredJwtException(null, null, "expired"));

        doFilter();

        assertThat(failureCount("expired")).isEqualTo(1.0);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void countsMalformedTokensByReason() throws Exception {
        when(tokenProvider.getUsernameFromToken(anyString()))
            .thenThrow(new MalformedJwtException("bad token"));

        doFilter();

        assertThat(failureCount("malformed")).isEqualTo(1.0);
    }

    @Test
    void countsRejectedTokensWhenValidationReturnsFalse() throws Exception {
        UserDetails userDetails = user();
        when(tokenProvider.getUsernameFromToken(anyString())).thenReturn("dr.house");
        when(userDetailsService.loadUserByUsername("dr.house")).thenReturn(userDetails);
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(false);

        doFilter();

        assertThat(failureCount("rejected")).isEqualTo(1.0);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void recordsNoFailureForValidToken() throws Exception {
        UserDetails userDetails = user();
        when(tokenProvider.getUsernameFromToken(anyString())).thenReturn("dr.house");
        when(userDetailsService.loadUserByUsername("dr.house")).thenReturn(userDetails);
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);

        doFilter();

        assertThat(meterRegistry.find(JwtAuthenticationFilter.VALIDATION_FAILURE_METRIC).counters()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private void doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", TOKEN);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private double failureCount(String reason) {
        return meterRegistry.counter(JwtAuthenticationFilter.VALIDATION_FAILURE_METRIC, "reason", reason).count();
    }

    private UserDetails user() {
        return new User("dr.house", "pw", AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
