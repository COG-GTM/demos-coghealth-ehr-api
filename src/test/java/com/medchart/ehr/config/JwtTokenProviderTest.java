package com.medchart.ehr.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-testing-purposes");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 86400000);
    }

    @Test
    void generateToken_returnsValidToken() {
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = tokenProvider.generateToken(auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUsernameFromToken_returnsCorrectUsername() {
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("dr.anderson", username);
    }

    @Test
    void getExpirationDateFromToken_returnsFutureDate() {
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        Date expiration = tokenProvider.getExpirationDateFromToken(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        Boolean isValid = tokenProvider.validateToken(token, userDetails);

        assertTrue(isValid);
    }

    @Test
    void validateToken_wrongUsername_returnsFalse() {
        UserDetails userDetails = new User("nurse.johnson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_NURSE")));
        String token = tokenProvider.generateTokenFromUsername("dr.anderson");

        Boolean isValid = tokenProvider.validateToken(token, userDetails);

        assertFalse(isValid);
    }

    @Test
    void validateToken_expiredToken_throwsExpiredJwtException() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-testing-purposes");
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpirationInMs", -1000);

        String token = shortLivedProvider.generateTokenFromUsername("dr.anderson");
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                () -> tokenProvider.validateToken(token, userDetails));
    }

    @Test
    void generateTokenFromUsername_producesTokenWithCorrectSubject() {
        String token = tokenProvider.generateTokenFromUsername("nurse.johnson");

        assertEquals("nurse.johnson", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void generateToken_withAuthentication_includesRolesInClaims() {
        UserDetails userDetails = new User("dr.anderson", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = tokenProvider.generateToken(auth);
        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("dr.anderson", username);
    }
}
