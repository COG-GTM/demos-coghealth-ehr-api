package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider();

    JwtTokenProviderTest() {
        ReflectionTestUtils.setField(provider, "jwtSecret", "x".repeat(64));
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000);
    }

    private User user(boolean enabled, boolean nonLocked) {
        return user(1L, enabled, nonLocked);
    }

    private User user(Long id, boolean enabled, boolean nonLocked) {
        return User.builder()
                .id(id)
                .username("clinic.admin")
                .password("unused")
                .roles(Set.of(User.Role.ADMIN))
                .enabled(enabled)
                .accountNonExpired(true)
                .accountNonLocked(nonLocked)
                .credentialsNonExpired(true)
                .build();
    }

    private String tokenFor(User user) {
        return provider.generateToken(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void acceptsTokenForEnabledUser() {
        User user = user(true, true);
        assertThat(provider.validateToken(tokenFor(user), user)).isTrue();
    }

    @Test
    void rejectsTokenOnceUserIsDisabled() {
        String token = tokenFor(user(true, true));
        assertThat(provider.validateToken(token, user(false, true))).isFalse();
    }

    @Test
    void rejectsTokenOnceUserIsLocked() {
        String token = tokenFor(user(true, true));
        assertThat(provider.validateToken(token, user(true, false))).isFalse();
    }

    @Test
    void rejectsTokenIssuedForEarlierAccountWithSameUsername() {
        String token = tokenFor(user(1L, true, true));
        User replacement = user(2L, true, true);

        assertThat(provider.validateToken(token, replacement)).isFalse();
    }

    @Test
    void rejectsTokenWithoutAccountId() {
        String token = Jwts.builder()
                .setSubject("clinic.admin")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("x".repeat(64).getBytes()), SignatureAlgorithm.HS512)
                .compact();

        assertThat(provider.validateToken(token, user(true, true))).isFalse();
    }
}
