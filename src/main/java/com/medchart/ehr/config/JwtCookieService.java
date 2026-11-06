package com.medchart.ehr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Builds and reads the HttpOnly session cookie that carries the JWT.
 *
 * <p>Keeping the token in an HttpOnly cookie means page JavaScript cannot read it,
 * so an XSS in the SPA cannot exfiltrate a usable session token.</p>
 */
@Component
public class JwtCookieService {

    @Value("${medchart.security.jwt.cookie-name:auth_token}")
    private String cookieName;

    @Value("${medchart.security.jwt.cookie-secure:true}")
    private boolean secure;

    @Value("${medchart.security.jwt.cookie-same-site:Lax}")
    private String sameSite;

    @Value("${medchart.security.jwt.expiration}")
    private int jwtExpirationInMs;

    public String getCookieName() {
        return cookieName;
    }

    public ResponseCookie createSessionCookie(String token) {
        return baseCookie(token)
            .maxAge(Duration.ofMillis(jwtExpirationInMs))
            .build();
    }

    public ResponseCookie clearSessionCookie() {
        return baseCookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/");
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> cookieName.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isEmpty())
            .findFirst();
    }
}
