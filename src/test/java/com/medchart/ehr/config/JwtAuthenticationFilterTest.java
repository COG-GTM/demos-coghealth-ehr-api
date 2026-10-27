package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledAccountIsNotAuthenticated() throws Exception {
        assertFalse(authenticate(user(false, true, true, true)));
    }

    @Test
    void lockedAccountIsNotAuthenticated() throws Exception {
        assertFalse(authenticate(user(true, false, true, true)));
    }

    @Test
    void enabledAccountIsAuthenticated() throws Exception {
        assertTrue(authenticate(user(true, true, true, true)));
    }

    private boolean authenticate(User user) throws Exception {
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn(user.getUsername());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
        when(tokenProvider.validateToken("valid-token", user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    private User user(boolean enabled, boolean unlocked, boolean accountNonExpired, boolean credentialsNonExpired) {
        return User.builder()
                .id(1L)
                .username("provider")
                .password("password")
                .email("provider@example.com")
                .firstName("Test")
                .lastName("Provider")
                .roles(Set.of(User.Role.PROVIDER))
                .enabled(enabled)
                .accountNonLocked(unlocked)
                .accountNonExpired(accountNonExpired)
                .credentialsNonExpired(credentialsNonExpired)
                .build();
    }
}
