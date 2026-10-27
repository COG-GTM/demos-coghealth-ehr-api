package com.medchart.ehr.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderSecretTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> providerWithSecret("").validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPublishedDefaultSecret() {
        assertThatThrownBy(() -> providerWithSecret(JwtTokenProvider.LEGACY_DEFAULT_SECRET).validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSecretTooShortForHs512() {
        assertThatThrownBy(() -> providerWithSecret("x".repeat(JwtTokenProvider.MIN_SECRET_BYTES - 1)).validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongSecret() {
        assertThatCode(() -> providerWithSecret("x".repeat(JwtTokenProvider.MIN_SECRET_BYTES)).validateSecret())
                .doesNotThrowAnyException();
    }

    private JwtTokenProvider providerWithSecret(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        return provider;
    }
}
