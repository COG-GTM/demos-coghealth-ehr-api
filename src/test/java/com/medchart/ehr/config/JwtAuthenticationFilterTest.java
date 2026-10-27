package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabledUserIsAuthenticatedAndFilterChainContinues() throws Exception {
        UserDetails user = user(false, false, false, false);
        MockFilterChain filterChain = invokeFilter(user);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertSame(user, authentication.getPrincipal());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    void disabledUserIsNotAuthenticatedAndFilterChainContinues() throws Exception {
        MockFilterChain filterChain = invokeFilter(user(true, false, false, false));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    void lockedUserIsNotAuthenticatedAndFilterChainContinues() throws Exception {
        MockFilterChain filterChain = invokeFilter(user(false, true, false, false));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    void expiredUserIsNotAuthenticatedAndFilterChainContinues() throws Exception {
        MockFilterChain filterChain = invokeFilter(user(false, false, true, false));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    void userWithExpiredCredentialsIsNotAuthenticatedAndFilterChainContinues() throws Exception {
        MockFilterChain filterChain = invokeFilter(user(false, false, false, true));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(filterChain.getRequest());
    }

    private MockFilterChain invokeFilter(UserDetails user) throws Exception {
        when(tokenProvider.getUsernameFromToken("x")).thenReturn("account");
        when(userDetailsService.loadUserByUsername("account")).thenReturn(user);
        when(tokenProvider.validateToken("x", user)).thenReturn(true);

        MockHttpServletRequest request = request();
        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        return filterChain;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer x");
        return request;
    }

    private UserDetails user(
            boolean disabled,
            boolean locked,
            boolean expired,
            boolean credentialsExpired) {
        return User.withUsername("account")
                .password("encoded")
                .authorities("ROLE_PROVIDER")
                .disabled(disabled)
                .accountLocked(locked)
                .accountExpired(expired)
                .credentialsExpired(credentialsExpired)
                .build();
    }
}
