package com.medchart.ehr.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    @Test
    void rejectsMissingShortOrLegacyDefaultSecret() {
        for (String secret : new String[] {null, "", "dev-secret-key-change-in-production", "short-secret"}) {
            assertThrows(IllegalStateException.class, () -> providerWith(secret).validateSecret());
        }
    }

    @Test
    void acceptsSufficientlyLongSecret() {
        assertDoesNotThrow(() -> providerWith("x".repeat(JwtTokenProvider.MIN_SECRET_BYTES)).validateSecret());
    }

    private static JwtTokenProvider providerWith(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        return provider;
    }
}
