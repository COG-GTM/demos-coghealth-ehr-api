package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String USERNAME = "dr.house";
    private static final String TOKEN = "header.payload.signature";

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    private final UserDetails userDetails = new User(USERNAME, "password",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_PHYSICIAN")));

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenAuthenticatesRequest() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
        when(tokenProvider.validateToken(TOKEN, userDetails)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PHYSICIAN");
        assertThat(authentication.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void missingAuthorizationHeaderLeavesContextEmpty() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(anyString(), any());
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void nonBearerAuthorizationHeaderLeavesContextEmpty() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(anyString(), any());
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void bearerHeaderWithoutTokenLeavesContextEmpty() throws Exception {
        request.addHeader("Authorization", "Bearer ");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(anyString(), any());
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void bearerPrefixIsStrippedExactly() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(tokenProvider.getUsernameFromToken(anyString())).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(tokenProvider).validateToken(eq(TOKEN), eq(userDetails));
    }

    @Test
    void invalidTokenLeavesContextEmpty() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);
        when(tokenProvider.validateToken(TOKEN, userDetails)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void tokenProviderFailureIsSwallowedAndRequestContinuesUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenThrow(new IllegalArgumentException("malformed token"));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void userLookupFailureIsSwallowedAndRequestContinuesUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenThrow(new UsernameNotFoundException(USERNAME));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void existingAuthenticationIsPreservedWhenNoTokenPresent() throws Exception {
        Authentication existing = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "preauth", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }
}
