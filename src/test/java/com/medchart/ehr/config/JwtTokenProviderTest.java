package com.medchart.ehr.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenProvider provider(String secret) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationInMs", 60000);
        return p;
    }

    @Test
    void rejectsMissingShortOrLegacyDefaultSecret() {
        assertThrows(IllegalStateException.class, () -> provider("").validateSecret());
        assertThrows(IllegalStateException.class, () -> provider("short-secret").validateSecret());
        assertThrows(IllegalStateException.class,
                () -> provider("dev-secret-key-change-in-production").validateSecret());
    }

    @Test
    void acceptsOwnHs512Token() {
        JwtTokenProvider p = provider(SECRET);
        p.validateSecret();
        String token = p.generateTokenFromUsername("admin");
        assertEquals("admin", p.getUsernameFromToken(token));
    }

    @Test
    void rejectsTokenWithOtherAlgorithm() {
        JwtTokenProvider p = provider(SECRET);
        String hs256 = Jwts.builder().setSubject("admin").setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        assertThrows(UnsupportedJwtException.class, () -> p.getUsernameFromToken(hs256));
    }
}
