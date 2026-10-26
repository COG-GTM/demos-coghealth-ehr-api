package com.medchart.ehr.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String LEGACY_DEFAULT_SECRET = "dev-secret-key-change-in-production";

    private static JwtTokenProvider provider(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000);
        provider.initSigningKey();
        return provider;
    }

    @Test
    void unsetSecretUsesRandomKeyThatCanIssueAndParseTokens() {
        JwtTokenProvider provider = provider("");

        String token = provider.generateTokenFromUsername("admin");

        assertThat(provider.getUsernameFromToken(token)).isEqualTo("admin");
    }

    @Test
    void unsetSecretRejectsTokenSignedWithLegacyPublishedDefault() {
        JwtTokenProvider provider = provider("");
        String forged = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(LEGACY_DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> provider.getUsernameFromToken(forged)).isInstanceOf(Exception.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> provider(LEGACY_DEFAULT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    @Test
    void sufficientlyLongSecretIsUsed() {
        String secret = "x".repeat(JwtTokenProvider.MIN_SECRET_BYTES);
        JwtTokenProvider issuer = provider(secret);
        JwtTokenProvider verifier = provider(secret);

        String token = issuer.generateTokenFromUsername("admin");

        assertThat(verifier.getUsernameFromToken(token)).isEqualTo("admin");
    }
}
