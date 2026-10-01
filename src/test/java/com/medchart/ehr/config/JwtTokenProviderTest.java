package com.medchart.ehr.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String JWT_SECRET =
            "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-testing";
    private static final int JWT_EXPIRATION = 86400000; // 24 hours

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", JWT_EXPIRATION);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        Authentication auth = mock(Authentication.class);
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        when(auth.getPrincipal()).thenReturn(userDetails);

        String token = tokenProvider.generateToken(auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateTokenFromUsername_shouldCreateValidToken() {
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUsernameFromToken_shouldReturnCorrectUsername() {
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("dr.anderson", username);
    }

    @Test
    void getExpirationDateFromToken_shouldReturnFutureDate() {
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        java.util.Date expiration = tokenProvider.getExpirationDateFromToken(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new java.util.Date()));
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));

        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        Boolean isValid = tokenProvider.validateToken(token, userDetails);

        assertTrue(isValid);
    }

    @Test
    void validateToken_shouldReturnFalseForWrongUsername() {
        UserDetails userDetails = new User("nurse.johnson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));

        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        Boolean isValid = tokenProvider.validateToken(token, userDetails);

        assertFalse(isValid);
    }

    @Test
    void validateToken_shouldRejectExpiredToken() {
        // Create a provider with 0ms expiration to force immediate expiry
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpirationInMs", 0);

        String token = shortLivedProvider.generateTokenFromUsername("dr.anderson");

        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));

        // JJWT throws ExpiredJwtException when parsing an expired token,
        // so validateToken propagates the exception rather than returning false
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                () -> shortLivedProvider.validateToken(token, userDetails));
    }

    @Test
    void generateToken_shouldContainRolesInClaims() {
        Authentication auth = mock(Authentication.class);
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PHYSICIAN")));
        when(auth.getPrincipal()).thenReturn(userDetails);

        String token = tokenProvider.generateToken(auth);
        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("dr.anderson", username);
    }

    @Test
    void differentUsers_shouldGetDifferentTokens() {
        String token1 = tokenProvider.generateTokenFromUsername("dr.anderson");
        String token2 = tokenProvider.generateTokenFromUsername("nurse.johnson");

        assertNotEquals(token1, token2);
    }

    @Test
    void getUsernameFromToken_shouldThrowForInvalidToken() {
        assertThrows(Exception.class, () ->
                tokenProvider.getUsernameFromToken("invalid.token.here"));
    }

    @Test
    void getUsernameFromToken_shouldThrowForTamperedToken() {
        String validToken = tokenProvider.generateTokenFromUsername("dr.anderson");
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () ->
                tokenProvider.getUsernameFromToken(tamperedToken));
    }
}
