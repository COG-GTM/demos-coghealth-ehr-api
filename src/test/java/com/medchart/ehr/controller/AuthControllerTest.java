package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtCookieService;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            new User("schen", "", Collections.emptyList()), null, Collections.emptyList());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.generateToken(any())).thenReturn("jwt-value");

        JwtCookieService cookieService = new JwtCookieService();
        ReflectionTestUtils.setField(cookieService, "cookieName", "auth_token");
        ReflectionTestUtils.setField(cookieService, "secure", true);
        ReflectionTestUtils.setField(cookieService, "sameSite", "Lax");
        ReflectionTestUtils.setField(cookieService, "jwtExpirationInMs", 86400000);

        AuthController controller = new AuthController(
            authenticationManager, mock(UserRepository.class), mock(PasswordEncoder.class),
            tokenProvider, cookieService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginSetsHttpOnlyCookieAndDoesNotReturnTokenInBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"schen\",\"password\":\"secret\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("auth_token=jwt-value");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jwt-value");
        assertThat(body).doesNotContain("\"token\"");
    }

    @Test
    void logoutClearsSessionCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("auth_token=");
        assertThat(setCookie).contains("Max-Age=0");
    }
}
