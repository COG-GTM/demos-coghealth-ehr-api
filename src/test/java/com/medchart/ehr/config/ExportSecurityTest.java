package com.medchart.ehr.config;

import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyExportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ExportSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportGenerator reportGenerator;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousCallerCannotGenerateReports() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/export/reports/daily")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2026-10-01T00:00:00")
                        .param("endDate", "2026-10-02T00:00:00"))
                .andExpect(status().isUnauthorized());

        verify(reportGenerator, never()).generatePatientRoster();
        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        givenToken("staff-token", "staff", "ROLE_STAFF");

        mockMvc.perform(get("/v1/export/reports/patient-roster").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());

        verify(reportGenerator, never()).generatePatientRoster();
    }

    @Test
    void adminReceivesReportAsAttachment() throws Exception {
        givenToken("admin-token", "admin", "ROLE_ADMIN");
        byte[] csv = "ID,MRN\n".getBytes(StandardCharsets.UTF_8);
        when(reportGenerator.generatePatientRoster()).thenReturn(csv);

        mockMvc.perform(get("/v1/export/reports/patient-roster").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(csv));
    }

    private void givenToken(String token, String username, String authority) {
        UserDetails user = User.withUsername(username).password("x").authorities(authority).build();
        when(tokenProvider.getUsernameFromToken(token)).thenReturn(username);
        when(tokenProvider.validateToken(eq(token), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
    }
}
