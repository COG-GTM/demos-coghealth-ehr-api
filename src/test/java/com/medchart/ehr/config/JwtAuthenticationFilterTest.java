package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static User user(boolean enabled, Set<User.Role> roles) {
        return User.builder()
            .username("mallory")
            .email("m@example.com")
            .firstName("M")
            .lastName("L")
            .password("hashed")
            .roles(new HashSet<>(roles))
            .enabled(enabled)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();
    }

    private static Authentication filterWith(User user) throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(tokenProvider.getUsernameFromToken("tok")).thenReturn("mallory");
        when(tokenProvider.validateToken(eq("tok"), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("mallory")).thenReturn(user);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/patients");
        request.addHeader("Authorization", "Bearer tok");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "request must continue down the chain");
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void tokenForPendingAccountIsNotAuthenticated() throws Exception {
        assertNull(filterWith(user(false, Set.of())));
    }

    @Test
    void tokenForDisabledProviderIsNotAuthenticated() throws Exception {
        assertNull(filterWith(user(false, Set.of(User.Role.PROVIDER))));
    }

    @Test
    void tokenForApprovedAccountCarriesItsRole() throws Exception {
        Authentication auth = filterWith(user(true, Set.of(User.Role.STAFF)));
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF")));
    }
}
