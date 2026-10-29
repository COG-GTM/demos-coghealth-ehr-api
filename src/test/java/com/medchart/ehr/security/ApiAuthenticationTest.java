package com.medchart.ehr.security;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.controller.AuthController;
import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.controller.PatientController;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import com.medchart.ehr.repository.UserRepository;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PatientController.class, LegacyExportController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "medchart.security.jwt.secret=" + ApiAuthenticationTest.JWT_SECRET)
class ApiAuthenticationTest {

    static final String JWT_SECRET = "test-only-jwt-secret-0123456789abcdef0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PatientService patientService;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        doThrow(new UsernameNotFoundException("unknown")).when(userDetailsService).loadUserByUsername(anyString());
        stubUser("nurse", User.Role.PROVIDER);
        stubUser("admin", User.Role.ADMIN);
        stubUser("disabled-admin", User.Role.ADMIN, false, true);
        stubUser("locked-admin", User.Role.ADMIN, true, false);
        when(patientService.getPatientById(1L)).thenReturn(new PatientDTO());
        when(reportGenerator.generateDailyReport()).thenReturn("mrn\n".getBytes());
    }

    @Test
    void patientReadWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isUnauthorized());
        verify(patientService, never()).getPatientById(1L);
    }

    @Test
    void patientReadWithValidTokenReturns200() throws Exception {
        mockMvc.perform(get("/v1/patients/1").header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isOk());
    }

    @Test
    void patientReadWithForgedTokenReturns401() throws Exception {
        mockMvc.perform(get("/v1/patients/1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithOtherKeyReturns401() throws Exception {
        String token = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "attacker-key-0123456789abcdef0123456789abcdef0123456789abcdef0123".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS512)
                .compact();
        mockMvc.perform(get("/v1/patients/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithDowngradedAlgorithmReturns401() throws Exception {
        String token = Jwts.builder()
                .setSubject("admin")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/v1/export/reports/daily").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void tokenForDisabledOrLockedAccountReturns401() throws Exception {
        for (String username : new String[] {"disabled-admin", "locked-admin"}) {
            mockMvc.perform(get("/v1/patients/1").header(HttpHeaders.AUTHORIZATION, bearer(username)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/v1/export/reports/daily").header(HttpHeaders.AUTHORIZATION, bearer(username)))
                    .andExpect(status().isUnauthorized());
        }
        verify(patientService, never()).getPatientById(1L);
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void phiExportWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isUnauthorized());
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void phiExportRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily").header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isForbidden());
        verify(reportGenerator, never()).generateDailyReport();

        mockMvc.perform(get("/v1/export/reports/daily").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsPublicAndIssuesUsableToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nurse\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void selfRegistrationIsNotPublic() throws Exception {
        String body = "{\"username\":\"mallory\",\"email\":\"m@example.com\",\"password\":\"pw\","
                + "\"firstName\":\"M\",\"lastName\":\"X\"}";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer("nurse")))
                .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());
    }

    private void stubUser(String username, User.Role role) {
        stubUser(username, role, true, true);
    }

    private void stubUser(String username, User.Role role, boolean enabled, boolean accountNonLocked) {
        User user = User.builder()
                .id(1L)
                .username(username)
                .password(passwordEncoder.encode("pw"))
                .email(username + "@example.com")
                .firstName(username)
                .lastName("Test")
                .roles(Set.of(role))
                .enabled(enabled)
                .accountNonExpired(true)
                .accountNonLocked(accountNonLocked)
                .credentialsNonExpired(true)
                .build();
        doReturn(user).when(userDetailsService).loadUserByUsername(username);
    }

    private String bearer(String username) {
        return "Bearer " + tokenProvider.generateTokenFromUsername(username);
    }
}
