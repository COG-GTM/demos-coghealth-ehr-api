package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationInMs", 3_600_000);
    }

    @Test
    void acceptsTokenForActiveUser() {
        User user = activeUser("bob");
        String token = tokenProvider.generateTokenFromUsername("bob");

        assertTrue(tokenProvider.validateToken(token, user));
    }

    @Test
    void rejectsTokenForDifferentUser() {
        String token = tokenProvider.generateTokenFromUsername("bob");

        assertFalse(tokenProvider.validateToken(token, activeUser("alice")));
    }

    @Test
    void rejectsStillValidTokenAfterAccountDisabled() {
        User user = activeUser("bob");
        String token = tokenProvider.generateTokenFromUsername("bob");
        user.setEnabled(false);

        assertFalse(tokenProvider.validateToken(token, user));
    }

    @Test
    void rejectsStillValidTokenAfterAccountLocked() {
        User user = activeUser("bob");
        String token = tokenProvider.generateTokenFromUsername("bob");
        user.setAccountNonLocked(false);

        assertFalse(tokenProvider.validateToken(token, user));
    }

    @Test
    void rejectsTokenForExpiredAccount() {
        User user = activeUser("bob");
        String token = tokenProvider.generateTokenFromUsername("bob");
        user.setAccountNonExpired(false);

        assertFalse(tokenProvider.validateToken(token, user));
    }

    @Test
    void rejectsTokenWhenCredentialsExpired() {
        User user = activeUser("bob");
        String token = tokenProvider.generateTokenFromUsername("bob");
        user.setCredentialsNonExpired(false);

        assertFalse(tokenProvider.validateToken(token, user));
    }

    @Test
    void rejectsTokenWhenUserMissing() {
        String token = tokenProvider.generateTokenFromUsername("bob");

        assertFalse(tokenProvider.validateToken(token, null));
    }

    static User activeUser(String username) {
        return User.builder()
                .username(username)
                .password("x")
                .email(username + "@riverbend.example")
                .firstName(username)
                .lastName("Test")
                .roles(Set.of(User.Role.STAFF))
                .build();
    }
}
