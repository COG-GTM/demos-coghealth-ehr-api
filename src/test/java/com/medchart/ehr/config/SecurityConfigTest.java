package com.medchart.ehr.config;

import com.medchart.ehr.controller.AuthController;
import com.medchart.ehr.controller.EncounterController;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.repository.UserRepository;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EncounterController.class, AuthController.class})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "medchart.security.jwt.secret=test-secret-key-for-security-config-test-0123456789",
        "medchart.security.jwt.expiration=60000"
})
class SecurityConfigTest {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername("dr.alice")).thenReturn(user("dr.alice", User.Role.PROVIDER));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(user("admin", User.Role.ADMIN));
        when(encounterService.findByIdWithDetails(anyLong())).thenReturn(Optional.of(new Encounter()));
    }

    @Test
    void anonymousRequestToPhiEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/1"))
                .andExpect(status().isUnauthorized());
        verify(encounterService, never()).findByIdWithDetails(anyLong());
    }

    @Test
    void anonymousWriteIsRejected() throws Exception {
        mockMvc.perform(post("/v1/encounters/1/cancel"))
                .andExpect(status().isUnauthorized());
        verify(encounterService, never()).cancelEncounter(anyLong());
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = io.jsonwebtoken.Jwts.builder()
                .setSubject("dr.alice")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "attacker-controlled-secret-key-0123456789-abcdefghij".getBytes()))
                .compact();

        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromLoginGrantsAccess() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dr.alice\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = login.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotSelfRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"email\":\"m@example.com\",\"password\":\"x\","
                                + "\"firstName\":\"M\",\"lastName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any());
    }

    @Test
    void providerCannotReachAdminOnlyEndpoints() throws Exception {
        String token = tokenProvider.generateTokenFromUsername("dr.alice");

        mockMvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/export/reports/patient-roster").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private User user(String username, User.Role role) {
        return User.builder()
                .id(1L)
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .email(username + "@medchart.test")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(role))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }
}
