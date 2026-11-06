package com.medchart.ehr.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieServiceTest {

    private JwtCookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new JwtCookieService();
        ReflectionTestUtils.setField(cookieService, "cookieName", "auth_token");
        ReflectionTestUtils.setField(cookieService, "secure", true);
        ReflectionTestUtils.setField(cookieService, "sameSite", "Lax");
        ReflectionTestUtils.setField(cookieService, "jwtExpirationInMs", 86400000);
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndScoped() {
        String header = cookieService.createSessionCookie("jwt-value").toString();

        assertThat(header).contains("auth_token=jwt-value");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Secure");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Max-Age=86400");
        assertThat(header).contains("Path=/");
    }

    @Test
    void clearSessionCookieExpiresImmediately() {
        String header = cookieService.clearSessionCookie().toString();

        assertThat(header).contains("auth_token=");
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains("HttpOnly");
    }

    @Test
    void readsTokenFromCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("auth_token", "jwt-value"));

        assertThat(cookieService.readToken(request)).contains("jwt-value");
    }

    @Test
    void readsNoTokenWhenCookieAbsentOrEmpty() {
        assertThat(cookieService.readToken(new MockHttpServletRequest())).isEmpty();

        MockHttpServletRequest emptyValue = new MockHttpServletRequest();
        emptyValue.setCookies(new Cookie("auth_token", ""));
        assertThat(cookieService.readToken(emptyValue)).isEmpty();
    }
}
