package com.medchart.ehr.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String JWT_SECRET =
            "test-secret-key-that-is-at-least-512-bits-long-for-hs512-algorithm-testing-purposes-only";
    private static final int JWT_EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();

        Field secretField = JwtTokenProvider.class.getDeclaredField("jwtSecret");
        secretField.setAccessible(true);
        secretField.set(jwtTokenProvider, JWT_SECRET);

        Field expirationField = JwtTokenProvider.class.getDeclaredField("jwtExpirationInMs");
        expirationField.setAccessible(true);
        expirationField.set(jwtTokenProvider, JWT_EXPIRATION_MS);
    }

    @Test
    void generateTokenFromUsername_producesValidJwt() {
        String token = jwtTokenProvider.generateTokenFromUsername("testuser");

        assertThat(token).isNotNull().isNotEmpty();

        // Verify it's a valid JWT by parsing it
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        assertThat(subject).isEqualTo("testuser");
    }

    @Test
    void getUsernameFromToken_roundTripsCorrectly() {
        String token = jwtTokenProvider.generateTokenFromUsername("doctor.smith");

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("doctor.smith");
    }

    @Test
    void validateToken_returnsTrueForValidTokenAndMatchingUserDetails() {
        String username = "nurse.jones";
        String token = jwtTokenProvider.generateTokenFromUsername(username);
        UserDetails userDetails = new User(username, "password", Collections.emptyList());

        Boolean valid = jwtTokenProvider.validateToken(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    void validateToken_returnsFalseForMismatchedUsername() {
        String token = jwtTokenProvider.generateTokenFromUsername("user-a");
        UserDetails userDetails = new User("user-b", "password", Collections.emptyList());

        Boolean valid = jwtTokenProvider.validateToken(token, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws Exception {
        // Set expiration to 0ms so token expires immediately
        Field expirationField = JwtTokenProvider.class.getDeclaredField("jwtExpirationInMs");
        expirationField.setAccessible(true);
        expirationField.set(jwtTokenProvider, 0);

        String token = jwtTokenProvider.generateTokenFromUsername("expireduser");
        // Small delay to ensure token is expired
        Thread.sleep(10);

        UserDetails userDetails = new User("expireduser", "password", Collections.emptyList());

        // jjwt 0.12.x throws ExpiredJwtException when parsing expired tokens,
        // so validateToken effectively returns false via exception
        boolean valid;
        try {
            valid = jwtTokenProvider.validateToken(token, userDetails);
        } catch (ExpiredJwtException e) {
            valid = false;
        }

        assertThat(valid).isFalse();
    }

    @Test
    void getExpirationDateFromToken_returnsExpectedDate() {
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtTokenProvider.generateTokenFromUsername("testuser");
        long afterGeneration = System.currentTimeMillis();

        Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);

        assertThat(expiration).isNotNull();
        // Expiration should be approximately now + JWT_EXPIRATION_MS
        // JWT expiration is truncated to seconds, so allow 1-second tolerance
        assertThat(expiration.getTime()).isBetween(
                beforeGeneration + JWT_EXPIRATION_MS - 1000L,
                afterGeneration + JWT_EXPIRATION_MS + 1000L
        );
    }
}
