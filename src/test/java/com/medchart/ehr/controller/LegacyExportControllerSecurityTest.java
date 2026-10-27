package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LegacyExportController.class)
@Import(SecurityConfig.class)
class LegacyExportControllerSecurityTest {

    private static final String ROSTER_CSV =
            "ID,MRN,FirstName,LastName,DOB,PhoneHome,PhoneMobile,Email,Address,City,State,Zip,Insurance,MemberID\n";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/export/reports/daily",
            "/v1/export/reports/patient-roster",
            "/v1/export/reports/encounter-summary?startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59",
            "/v1/export/encounters?startDate=2024-01-01&endDate=2024-12-31",
            "/v1/export/patient/1/encounters"
    })
    void anonymousExportRequestsAreRejectedWith401(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());

        verifyNoInteractions(reportGenerator, encounterExportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/export/reports/daily",
            "/v1/export/reports/patient-roster",
            "/v1/export/encounters?startDate=2024-01-01&endDate=2024-12-31",
            "/v1/export/patient/1/encounters"
    })
    @WithMockUser(roles = {"PROVIDER", "STAFF"})
    void nonAdminExportRequestsAreForbidden(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isForbidden());

        verifyNoInteractions(reportGenerator, encounterExportService);
    }

    @Test
    void invalidBearerTokenIsRejectedWith401() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad token"));

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reportGenerator);
    }

    @Test
    void adminBearerTokenCanDownloadDailyReport() throws Exception {
        User admin = User.builder()
                .id(1L)
                .username("compliance.admin")
                .password("x")
                .roles(Set.of(User.Role.ADMIN))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        when(jwtTokenProvider.getUsernameFromToken("valid")).thenReturn("compliance.admin");
        when(userDetailsService.loadUserByUsername("compliance.admin")).thenReturn(admin);
        when(jwtTokenProvider.validateToken(eq("valid"), any())).thenReturn(true);
        when(reportGenerator.generateDailyReport()).thenReturn(ROSTER_CSV.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string(ROSTER_CSV));
    }

    @Test
    void providerBearerTokenCannotDownloadDailyReport() throws Exception {
        User provider = User.builder()
                .id(2L)
                .username("dr.smith")
                .password("x")
                .roles(Set.of(User.Role.PROVIDER))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        when(jwtTokenProvider.getUsernameFromToken("provider")).thenReturn("dr.smith");
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(provider);
        when(jwtTokenProvider.validateToken(eq("provider"), any())).thenReturn(true);

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer provider"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportGenerator);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanExportPatientEncounterHistory() throws Exception {
        when(encounterExportService.exportPatientEncounterHistory(7L))
                .thenReturn("Patient Encounter History Report\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/patient/7/encounters")).andExpect(status().isOk());
    }

    @Test
    void anonymousRequestsToOtherApiRoutesAreRejectedWith401() throws Exception {
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isUnauthorized());
    }
}
