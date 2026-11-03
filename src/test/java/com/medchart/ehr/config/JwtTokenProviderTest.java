package com.medchart.ehr.config;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    static final String OLD_COMMITTED_DEFAULT = "dev-secret-key-change-in-production";

    static String strongSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    static JwtTokenProvider provider(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000);
        provider.init();
        return provider;
    }

    static String forge(String key, SignatureAlgorithm alg, String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), alg)
                .compact();
    }

    @Test
    void contextFailsToStartWhenJwtSecretIsUnset() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(JwtTokenProvider.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Could not resolve placeholder 'JWT_SECRET' in value \"${JWT_SECRET}\"");
                });
    }

    @Test
    void contextStartsWithStrongJwtSecret() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withPropertyValues("JWT_SECRET=" + strongSecret())
                .withUserConfiguration(JwtTokenProvider.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class));
    }

    @Test
    void rejectsMissingBlankShortAndLowEntropySecrets() {
        assertThrows(IllegalStateException.class, () -> provider(null));
        assertThrows(IllegalStateException.class, () -> provider("   "));
        assertThrows(IllegalStateException.class, () -> provider(OLD_COMMITTED_DEFAULT));
        assertThrows(IllegalStateException.class, () -> provider(strongSecret().substring(0, 63)));
        StringBuilder repeated = new StringBuilder();
        while (repeated.length() < 128) {
            repeated.append("abc123");
        }
        assertThrows(IllegalStateException.class, () -> provider(repeated.toString()));
    }

    @Test
    void issuesAndValidatesHs512Tokens() {
        JwtTokenProvider provider = provider(strongSecret());
        String token = provider.generateTokenFromUsername("dr.smith");

        assertEquals("dr.smith", provider.getUsernameFromToken(token));
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS512\"");
    }

    @Test
    void rejectsTokenSignedWithOldCommittedDefault() {
        JwtTokenProvider provider = provider(strongSecret());
        String forged = forge(OLD_COMMITTED_DEFAULT, SignatureAlgorithm.HS256, "admin");

        assertThrows(JwtException.class, () -> provider.getUsernameFromToken(forged));
    }

    @Test
    void rejectsTokenSignedWithAnotherHmacAlgorithmEvenWithTheRightKey() {
        String secret = strongSecret();
        JwtTokenProvider provider = provider(secret);

        assertThrows(JwtException.class,
                () -> provider.getUsernameFromToken(forge(secret, SignatureAlgorithm.HS256, "admin")));
        assertThrows(JwtException.class,
                () -> provider.getUsernameFromToken(forge(secret, SignatureAlgorithm.HS384, "admin")));
    }

    @Test
    void rejectsUnsignedToken() {
        JwtTokenProvider provider = provider(strongSecret());
        String unsigned = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .compact();

        assertThrows(JwtException.class, () -> provider.getUsernameFromToken(unsigned));
    }
}
