package com.medchart.ehr.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void rejectsSecretsTooShortForHs512() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "dev-secret-key-change-in-production");

        assertThatThrownBy(provider::initSigningKey).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatesRandomKeyWhenSecretUnset() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60000);
        provider.initSigningKey();

        String token = provider.generateTokenFromUsername("dr.alice");

        assertThat(provider.getUsernameFromToken(token)).isEqualTo("dr.alice");
    }
}
