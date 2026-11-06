package com.medchart.ehr.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-that-is-long-enough-for-hs512-signing-0123456789abcdef";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(SECRET, 60_000);
    }

    @Test
    void generatedTokenCarriesUsernameAsSubject() {
        String token = provider.generateTokenFromUsername("dr.smith");

        assertThat(provider.getUsernameFromToken(token)).isEqualTo("dr.smith");
    }

    @Test
    void generatedTokenExpiresAfterConfiguredDuration() {
        long before = System.currentTimeMillis();
        String token = provider.generateTokenFromUsername("dr.smith");

        Date expiration = provider.getExpirationDateFromToken(token);

        assertThat(expiration.getTime()).isBetween(before + 59_000, before + 61_000);
    }

    @Test
    void tokenFromAuthenticationValidatesForSameUser() {
        UserDetails user = User.withUsername("nurse.jones").password("x").roles("NURSE").build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        String token = provider.generateToken(auth);

        assertThat(provider.validateToken(token, user)).isTrue();
    }

    @Test
    void tokenDoesNotValidateForDifferentUser() {
        String token = provider.generateTokenFromUsername("dr.smith");
        UserDetails other = User.withUsername("dr.who").password("x").roles("PHYSICIAN").build();

        assertThat(provider.validateToken(token, other)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtTokenProvider otherProvider = newProvider(SECRET + "-other", 60_000);
        String foreignToken = otherProvider.generateTokenFromUsername("dr.smith");

        assertThatThrownBy(() -> provider.getUsernameFromToken(foreignToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider expiringProvider = newProvider(SECRET, -1_000);
        String token = expiringProvider.generateTokenFromUsername("dr.smith");

        assertThatThrownBy(() -> provider.getUsernameFromToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    private static JwtTokenProvider newProvider(String secret, int expirationMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationInMs", expirationMs);
        return p;
    }
}
