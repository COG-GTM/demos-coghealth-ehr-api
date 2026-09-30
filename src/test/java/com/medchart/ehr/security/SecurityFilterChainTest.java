package com.medchart.ehr.security;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.support.TestContainers;
import com.medchart.testsupport.WhoAmIController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterizes the current security posture: every route is permitAll, and the JWT filter only
 * decorates the SecurityContext (after authorization has already run) without ever rejecting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WhoAmIController.class)
class SecurityFilterChainTest {

    private static final String USERNAME = "jwt.filter.user";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        TestContainers.register(registry);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void ensureUser() {
        jdbc.update("INSERT INTO users (username, password, email, first_name, last_name, enabled) "
                + "VALUES (?, 'x', ?, 'Jwt', 'Filter', true) ON CONFLICT (username) DO UPDATE SET enabled = true",
                USERNAME, USERNAME + "@example.com");
        jdbc.update("INSERT INTO user_roles (user_id, role) SELECT id, 'STAFF' FROM users WHERE username = ? "
                + "ON CONFLICT DO NOTHING", USERNAME);
    }

    @Test
    void requestsWithoutTokenAreAnonymousButAllowed() throws Exception {
        whoAmI(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AnonymousAuthenticationToken"))
                .andExpect(jsonPath("$.name").value("anonymousUser"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_ANONYMOUS"));

        mvc.perform(get("/v1/patients/1")).andExpect(status().isOk());
        mvc.perform(post("/v1/patients").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validTokenPopulatesSecurityContextFromUserDetails() throws Exception {
        whoAmI("Bearer " + tokenProvider.generateTokenFromUsername(USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("UsernamePasswordAuthenticationToken"))
                .andExpect(jsonPath("$.name").value(USERNAME))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_STAFF"));
    }

    @Test
    void disabledUserWithValidTokenIsStillAuthenticated() throws Exception {
        jdbc.update("UPDATE users SET enabled = false WHERE username = ?", USERNAME);

        whoAmI("Bearer " + tokenProvider.generateTokenFromUsername(USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(USERNAME));
    }

    @Test
    void invalidTokensFallBackToAnonymousInsteadOfBeingRejected() throws Exception {
        String valid = tokenProvider.generateTokenFromUsername(USERNAME);
        String expired = JwtTokenProviderTest.provider(JwtTokenProviderTest.SECRET, -1_000)
                .generateTokenFromUsername(USERNAME);
        String foreign = JwtTokenProviderTest.provider(JwtTokenProviderTest.SECRET.replace('t', 'T'), 60_000)
                .generateTokenFromUsername(USERNAME);
        String unknownUser = tokenProvider.generateTokenFromUsername("no.such.user");

        for (String header : new String[] {
                "Bearer garbage", "Bearer " + expired, "Bearer " + foreign, "Bearer " + unknownUser,
                "bearer " + valid, "Token " + valid, "Bearer "}) {
            whoAmI(header)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("anonymousUser"));
        }
        mvc.perform(get("/v1/patients/1").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void jwtFilterIsAPlainServletFilterOutsideTheSecurityChain() {
        assertThat(springSecurityFilterChain.getFilters("/v1/patients/1"))
                .noneMatch(filter -> filter instanceof JwtAuthenticationFilter);
        assertThat(jwtAuthenticationFilter).isNotInstanceOf(Ordered.class);
    }

    @Test
    void corsAllowsConfiguredDevOriginsOnly() throws Exception {
        mvc.perform(options("/v1/patients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS"));

        mvc.perform(options("/v1/patients")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());

        mvc.perform(options("/v1/patients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/patients/1").header(HttpHeaders.ORIGIN, "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statelessSessionAndNoCsrf() throws Exception {
        mvc.perform(get("/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private ResultActions whoAmI(String authorization) throws Exception {
        if (authorization == null) {
            return mvc.perform(get("/test/whoami"));
        }
        return mvc.perform(get("/test/whoami").header(HttpHeaders.AUTHORIZATION, authorization));
    }
}
