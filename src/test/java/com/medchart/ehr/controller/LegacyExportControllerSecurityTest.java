package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyExportController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "medchart.security.jwt.secret=test-secret-key-that-is-long-enough-for-hs512-signing-0123456789abcdef",
        "medchart.security.jwt.expiration=60000"
})
class LegacyExportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousDailyReportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isUnauthorized());
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void anonymousPatientEncounterExportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/patient/7/encounters"))
                .andExpect(status().isUnauthorized());
        verify(encounterExportService, never()).exportPatientEncounterHistory(any());
    }

    @Test
    void anonymousEncounterRangeExportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2000-01-01")
                        .param("endDate", "2099-01-01"))
                .andExpect(status().isUnauthorized());
        verify(encounterExportService, never()).exportEncountersForDateRange(any(), any());
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer bogus"))
                .andExpect(status().isUnauthorized());
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void nonAdminTokenIsForbidden() throws Exception {
        String token = tokenFor("provider1", User.Role.PROVIDER);
        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/export/patient/7/encounters").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verify(reportGenerator, never()).generateDailyReport();
        verify(encounterExportService, never()).exportPatientEncounterHistory(any());
    }

    @Test
    void adminTokenCanDownloadDailyReport() throws Exception {
        String token = tokenFor("admin", User.Role.ADMIN);
        when(reportGenerator.generateDailyReport()).thenReturn("ID,MRN,SSN\n".getBytes());
        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(reportGenerator).generateDailyReport();
    }

    @Test
    void disabledAdminTokenIsUnauthorized() throws Exception {
        String token = tokenFor("disabled-admin", User.Role.ADMIN, false);
        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verify(reportGenerator, never()).generateDailyReport();
    }

    private String tokenFor(String username, User.Role role) {
        return tokenFor(username, role, true);
    }

    private String tokenFor(String username, User.Role role, boolean enabled) {
        User user = User.builder()
                .username(username)
                .password("x")
                .email(username + "@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(role))
                .enabled(enabled)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        return jwtTokenProvider.generateTokenFromUsername(username);
    }
}
