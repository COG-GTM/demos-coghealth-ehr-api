package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    static final String USER_ID_CLAIM = "uid";

    @Value("${medchart.security.jwt.secret}")
    private String jwtSecret;

    @Value("${medchart.security.jwt.expiration}")
    private int jwtExpirationInMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userPrincipal.getAuthorities());
        if (userPrincipal instanceof User && ((User) userPrincipal).getId() != null) {
            claims.put(USER_ID_CLAIM, ((User) userPrincipal).getId());
        }
        
        return createToken(claims, userPrincipal.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return username.equals(userDetails.getUsername())
                && userDetails.isEnabled()
                && userDetails.isAccountNonLocked()
                && !isTokenExpired(token)
                && issuedForSameAccount(token, userDetails);
    }

    // Binds the token to the account id, so a token minted for a retired account
    // cannot authenticate a newer account that reused its username.
    private boolean issuedForSameAccount(String token, UserDetails userDetails) {
        if (!(userDetails instanceof User) || ((User) userDetails).getId() == null) {
            return true;
        }
        Object tokenUserId = getClaimFromToken(token, claims -> claims.get(USER_ID_CLAIM));
        return tokenUserId instanceof Number
                && ((Number) tokenUserId).longValue() == ((User) userDetails).getId();
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}