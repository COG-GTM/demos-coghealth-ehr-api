package com.medchart.ehr.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.ExpiredJwtException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-must-be-at-least-64-bytes-long-for-hs512-algorithm-testing");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 86400000);
    }

    @Test
    void generateToken_shouldReturnValidJwt() {
        Authentication auth = createAuthentication("testuser");

        String token = tokenProvider.generateToken(auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void getUsernameFromToken_shouldExtractUsername() {
        Authentication auth = createAuthentication("testuser");
        String token = tokenProvider.generateToken(auth);

        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("testuser", username);
    }

    @Test
    void generateTokenFromUsername_shouldCreateToken() {
        String token = tokenProvider.generateTokenFromUsername("admin");

        assertNotNull(token);
        assertEquals("admin", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    void getExpirationDateFromToken_shouldReturnFutureDate() {
        String token = tokenProvider.generateTokenFromUsername("testuser");

        Date expiration = tokenProvider.getExpirationDateFromToken(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void validateToken_shouldReturnTrue_forValidToken() {
        UserDetails userDetails = new User("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = tokenProvider.generateToken(auth);

        Boolean isValid = tokenProvider.validateToken(token, userDetails);

        assertTrue(isValid);
    }

    @Test
    void validateToken_shouldReturnFalse_forWrongUser() {
        UserDetails userDetails = new User("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        UserDetails otherUser = new User("otheruser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = tokenProvider.generateToken(auth);

        Boolean isValid = tokenProvider.validateToken(token, otherUser);

        assertFalse(isValid);
    }

    @Test
    void validateToken_shouldThrowExpiredJwtException_forExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret",
                "test-secret-key-must-be-at-least-64-bytes-long-for-hs512-algorithm-testing");
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpirationInMs", 0);

        UserDetails userDetails = new User("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = shortLivedProvider.generateToken(auth);

        assertThrows(ExpiredJwtException.class,
                () -> shortLivedProvider.validateToken(token, userDetails));
    }

    @Test
    void getClaimFromToken_shouldExtractSubject() {
        String token = tokenProvider.generateTokenFromUsername("claimuser");

        String subject = tokenProvider.getClaimFromToken(token, claims -> claims.getSubject());

        assertEquals("claimuser", subject);
    }

    private Authentication createAuthentication(String username) {
        UserDetails userDetails = new User(username, "password",
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
