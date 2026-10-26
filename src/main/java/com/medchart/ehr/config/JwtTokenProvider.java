package com.medchart.ehr.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512;
    static final int MIN_SECRET_BYTES = 64;
    static final int MIN_DISTINCT_BYTES = 16;

    @Value("${medchart.security.jwt.secret}")
    private String jwtSecret;

    @Value("${medchart.security.jwt.expiration}")
    private int jwtExpirationInMs;

    private SecretKey signingKey;
    private JwtParser parser;

    @PostConstruct
    void init() {
        signingKey = Keys.hmacShaKeyFor(validatedSecret(jwtSecret));
        parser = Jwts.parserBuilder().setSigningKey(signingKey).build();
    }

    static byte[] validatedSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "medchart.security.jwt.secret is empty; set JWT_SECRET to at least "
                            + MIN_SECRET_BYTES + " random bytes (e.g. openssl rand -base64 64)");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is " + bytes.length + " bytes; " + SIGNATURE_ALGORITHM.getValue()
                            + " requires at least " + MIN_SECRET_BYTES + " random bytes (e.g. openssl rand -base64 64)");
        }
        if (distinctByteCount(bytes) < MIN_DISTINCT_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET has too little entropy; generate it with e.g. openssl rand -base64 64");
        }
        return bytes;
    }

    private static int distinctByteCount(byte[] bytes) {
        boolean[] seen = new boolean[256];
        int distinct = 0;
        for (byte b : bytes) {
            if (!seen[b & 0xFF]) {
                seen[b & 0xFF] = true;
                distinct++;
            }
        }
        return distinct;
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userPrincipal.getAuthorities());
        
        return createToken(claims, userPrincipal.getUsername());
    }

    public String generateTokenFromUsername(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SIGNATURE_ALGORITHM)
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
        Jws<Claims> jws = parser.parseClaimsJws(token);
        if (!SIGNATURE_ALGORITHM.getValue().equals(jws.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Unexpected JWT signature algorithm: " + jws.getHeader().getAlgorithm());
        }
        return jws.getBody();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}
