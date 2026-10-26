package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    @Test
    void defaultDevSecretCanIssueAndValidateTokens() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "dev-secret-key-change-in-production");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000);
        User user = User.builder().username("dr.smith").password("x").roles(Set.of(User.Role.PROVIDER)).build();

        String token = provider.generateToken(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        assertEquals("dr.smith", provider.getUsernameFromToken(token));
        assertTrue(provider.validateToken(token, user));
    }
}
