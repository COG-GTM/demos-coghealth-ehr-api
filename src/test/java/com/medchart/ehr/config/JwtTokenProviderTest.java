package com.medchart.ehr.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
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
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-for-hs512-which-must-be-at-least-64-bytes-long-to-be-valid";
    private static final int EXPIRATION_MS = 60_000;

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", EXPIRATION_MS);
    }

    private UserDetails user(String username) {
        return new User(username, "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PHYSICIAN")));
    }

    @Test
    void generatedTokenRoundTripsUsernameAndExpiration() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user("dr.smith"), "password",
                        user("dr.smith").getAuthorities());

        String token = tokenProvider.generateToken(authentication);

        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("dr.smith");
        assertThat(tokenProvider.getExpirationDateFromToken(token)).isAfter(new Date());
    }

    @Test
    void generateTokenFromUsernameRoundTripsUsername() {
        String token = tokenProvider.generateTokenFromUsername("nurse.jones");

        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("nurse.jones");
    }

    @Test
    void validateTokenAcceptsValidTokenForMatchingUser() {
        String token = tokenProvider.generateTokenFromUsername("dr.smith");

        assertThat(tokenProvider.validateToken(token, user("dr.smith"))).isTrue();
    }

    @Test
    void validateTokenRejectsTokenForDifferentUser() {
        String token = tokenProvider.generateTokenFromUsername("dr.smith");

        assertThat(tokenProvider.validateToken(token, user("dr.jones"))).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", -1000);
        String token = tokenProvider.generateTokenFromUsername("dr.smith");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", EXPIRATION_MS);

        assertThatThrownBy(() -> tokenProvider.validateToken(token, user("dr.smith")))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreignSecret =
                "another-secret-key-for-hs512-which-must-also-be-at-least-64-bytes-long";
        String token = Jwts.builder()
                .setClaims(new HashMap<>())
                .setSubject("dr.smith")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(Keys.hmacShaKeyFor(foreignSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();

        assertThatThrownBy(() -> tokenProvider.validateToken(token, user("dr.smith")))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenProvider.generateTokenFromUsername("dr.smith");
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("aa") ? "bb" : "aa");

        assertThatThrownBy(() -> tokenProvider.getUsernameFromToken(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> tokenProvider.getUsernameFromToken("not-a-jwt"))
                .isInstanceOf(io.jsonwebtoken.MalformedJwtException.class);
    }
}
