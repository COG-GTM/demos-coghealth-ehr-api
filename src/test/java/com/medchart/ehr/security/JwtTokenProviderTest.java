package com.medchart.ehr.security;

import com.medchart.ehr.config.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    static final String SECRET = "test-only-hs512-signing-key-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGH";
    private static final String APPLICATION_DEFAULT_SECRET = "dev-secret-key-change-in-production";
    private static final int ONE_DAY_MS = 86_400_000;

    private final JwtTokenProvider provider = provider(SECRET, ONE_DAY_MS);

    @Test
    void tokenFromUsernameRoundTripsSubjectAndExpiry() {
        long before = System.currentTimeMillis();
        String token = provider.generateTokenFromUsername("dr.house");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("dr.house");
        Date expiry = provider.getExpirationDateFromToken(token);
        assertThat(expiry.getTime()).isBetween(before + ONE_DAY_MS - 2_000, before + ONE_DAY_MS + 2_000);
    }

    @Test
    void tokensAreHs512SignedWithTheRawSecretBytes() {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(provider.generateTokenFromUsername("dr.house"));

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS512");
        assertThat(jws.getBody().getSubject()).isEqualTo("dr.house");
        assertThat(jws.getBody().getIssuedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenFromAuthenticationCarriesRolesClaim() {
        UserDetails user = User.withUsername("nurse").password("x").roles("PROVIDER", "STAFF").build();
        String token = provider.generateToken(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        Object roles = provider.getClaimFromToken(token, claims -> claims.get("roles"));
        assertThat(roles).isInstanceOf(List.class);
        assertThat((List<Object>) roles).containsExactlyInAnyOrder(
                Map.of("authority", "ROLE_PROVIDER"), Map.of("authority", "ROLE_STAFF"));
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("nurse");
    }

    @Test
    void validateTokenComparesSubjectWithUserDetails() {
        String token = provider.generateTokenFromUsername("dr.house");

        assertThat(provider.validateToken(token, user("dr.house"))).isTrue();
        assertThat(provider.validateToken(token, user("someone.else"))).isFalse();
    }

    @Test
    void expiredTokensThrowInsteadOfReturningFalse() {
        String expired = provider(SECRET, -1_000).generateTokenFromUsername("dr.house");

        assertThatThrownBy(() -> provider.validateToken(expired, user("dr.house")))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = provider.generateTokenFromUsername("dr.house");
        String[] parts = token.split("\\.");
        String forgedPayload = Jwts.builder().setSubject("admin").compact().split("\\.")[1];
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];
        String foreign = provider(SECRET.replace('t', 'T'), ONE_DAY_MS).generateTokenFromUsername("dr.house");

        assertThatThrownBy(() -> provider.getUsernameFromToken(tampered)).isInstanceOf(SignatureException.class);
        assertThatThrownBy(() -> provider.getUsernameFromToken(foreign)).isInstanceOf(SignatureException.class);
        assertThatThrownBy(() -> provider.getUsernameFromToken("not-a-jwt")).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void applicationDefaultSecretIsTooShortForHs512() {
        JwtTokenProvider defaultProvider = provider(APPLICATION_DEFAULT_SECRET, ONE_DAY_MS);

        assertThatThrownBy(() -> defaultProvider.generateTokenFromUsername("dr.house"))
                .isInstanceOf(WeakKeyException.class)
                .hasMessageContaining("280 bits");
    }

    static JwtTokenProvider provider(String secret, int expirationMs) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", expirationMs);
        return provider;
    }

    private static UserDetails user(String username) {
        return User.withUsername(username).password("x").roles("PROVIDER").build();
    }
}
